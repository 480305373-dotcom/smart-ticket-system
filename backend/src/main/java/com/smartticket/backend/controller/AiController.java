package com.smartticket.backend.controller;

import com.smartticket.backend.dto.AiChatRequest;
import com.smartticket.backend.dto.ApiResponse;
import com.smartticket.backend.dto.CreateTicketFromDraftRequest;
import com.smartticket.backend.dto.KnowledgeRequest;
import com.smartticket.backend.dto.TicketAiAnalysisRequest;
import com.smartticket.backend.dto.TicketAiAnalysisResponse;
import com.smartticket.backend.dto.TicketDraftRequest;
import com.smartticket.backend.dto.TicketDraftResponse;
import com.smartticket.backend.entity.Ticket;
import com.smartticket.backend.enums.TicketStatus;
import com.smartticket.backend.security.AuthUser;
import com.smartticket.backend.service.IdempotencyService;
import com.smartticket.backend.service.KnowledgeService;
import com.smartticket.backend.service.OperationLogService;
import com.smartticket.backend.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.context.annotation.Profile("!dev")
@RestController
@RequestMapping("/ai")
public class AiController {

  private final ChatClient chatClient;
  private final KnowledgeService knowledgeService;
  private final TicketService ticketService;
  private final OperationLogService operationLogService;
  private final IdempotencyService idempotencyService;

  public AiController(
      ChatClient.Builder builder,
      KnowledgeService knowledgeService,
      TicketService ticketService,
      OperationLogService operationLogService,
      IdempotencyService idempotencyService) {
    this.chatClient = builder.build();
    this.knowledgeService = knowledgeService;
    this.ticketService = ticketService;
    this.operationLogService = operationLogService;
    this.idempotencyService = idempotencyService;
  }

  // 普通 AI 聊天
  @PostMapping("/chat")
  public ApiResponse<String> chat(@Valid @RequestBody AiChatRequest request) {

    String answer = chatClient.prompt().user(request.getMessage()).call().content();

    return ApiResponse.success(answer);
  }

  // 管理员录入知识
  @PostMapping("/knowledge")
  public ApiResponse<Void> addKnowledge(
      @Valid @RequestBody KnowledgeRequest request, Authentication authentication) {

    AuthUser user = (AuthUser) authentication.getPrincipal();

    if (!"ADMIN".equals(user.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以录入知识");
    }

    knowledgeService.addKnowledge(request);

    return ApiResponse.success(null);
  }

  // RAG 智能问答
  @PostMapping("/rag")
  public ApiResponse<String> rag(@Valid @RequestBody AiChatRequest request) {

    List<Document> documents = knowledgeService.search(request.getMessage());

    String context = documents.stream().map(Document::getText).collect(Collectors.joining("\n\n"));

    String answer =
        chatClient
            .prompt()
            .system(
                """
                你是企业智能工单系统的知识助手。

                请优先根据下面提供的企业知识库内容回答问题。

                如果知识库内容无法回答问题，
                请明确回答：
                “知识库中暂未找到相关信息。”

                不要编造不存在的企业规定。

                企业知识库内容：
                """
                    + context)
            .user(request.getMessage())
            .call()
            .content();

    return ApiResponse.success(answer);
  }

  @PostMapping("/ticket-analysis")
  public ApiResponse<TicketAiAnalysisResponse> analyzeTicket(
      @Valid @RequestBody TicketAiAnalysisRequest request) {

    List<Document> documents = knowledgeService.search(request.getDescription());

    String context = documents.stream().map(Document::getText).collect(Collectors.joining("\n\n"));

    String suggestion =
        chatClient
            .prompt()
            .system(
                """
                你是企业 IT 工单分析助手。

                根据企业知识库和用户描述，
                给出简洁、可执行的故障处理建议。

                不要编造企业内部规定。

                企业知识库：
                """
                    + context)
            .user(request.getDescription())
            .call()
            .content();

    int priority = determinePriority(request.getDescription());

    return ApiResponse.success(new TicketAiAnalysisResponse(suggestion, priority));
  }

  private int determinePriority(String description) {

    String text = description.toLowerCase();

    if (text.contains("系统崩溃")
        || text.contains("无法登录")
        || text.contains("全部用户")
        || text.contains("服务不可用")) {
      return 5;
    }

    if (text.contains("vpn")
        || text.contains("网络")
        || text.contains("连接失败")
        || text.contains("无法连接")) {
      return 4;
    }

    if (text.contains("报错") || text.contains("异常") || text.contains("故障")) {
      return 3;
    }

    return 2;
  }

  @PostMapping("/ticket-draft")
  public ApiResponse<TicketDraftResponse> createTicketDraft(
      @Valid @RequestBody TicketDraftRequest request) {

    List<Document> documents = knowledgeService.search(request.getDescription());

    String context = documents.stream().map(Document::getText).collect(Collectors.joining("\n\n"));

    String title =
        chatClient
            .prompt()
            .system(
                """
                你是企业工单系统助手。

                根据用户的故障描述生成一个简短工单标题。

                要求：
                1. 标题控制在20个汉字以内
                2. 只返回标题
                3. 不要解释
                4. 不要加引号
                """)
            .user(request.getDescription())
            .call()
            .content();

    String suggestion =
        chatClient
            .prompt()
            .system(
                """
                你是企业 IT 工单处理助手。

                根据企业知识库给用户提供简洁、
                可执行的故障处理建议。

                不要编造企业内部规定。

                企业知识库：
                """
                    + context)
            .user(request.getDescription())
            .call()
            .content();

    int priority = determinePriority(request.getDescription());

    TicketDraftResponse response =
        new TicketDraftResponse(title, request.getDescription(), suggestion, priority);

    return ApiResponse.success(response);
  }

  @PostMapping("/ticket-create")
  public ApiResponse<Ticket> createTicketFromDraft(
      @Valid @RequestBody CreateTicketFromDraftRequest request,
      @RequestHeader("X-Request-Id") String requestId,
      Authentication authentication) {

    AuthUser user = (AuthUser) authentication.getPrincipal();

    boolean acquired = idempotencyService.tryAcquire(user.userId(), requestId);

    if (!acquired) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "请勿重复提交工单");
    }

    try {

      Ticket ticket = new Ticket();

      ticket.setTitle(request.getTitle());
      ticket.setDescription(request.getDescription());
      ticket.setPriority(request.getPriority());
      ticket.setStatus(TicketStatus.OPEN);
      ticket.setUserId(user.userId());

      Ticket created = ticketService.createTicket(ticket);

      operationLogService.record(
          user.userId(), "AI_CREATE_TICKET", created.getId(), "通过AI工单草稿创建工单");

      return ApiResponse.success(created);

    } catch (Exception e) {

      // 创建失败则释放幂等Key，允许用户重试
      idempotencyService.release(user.userId(), requestId);

      throw e;
    }
  }
}
