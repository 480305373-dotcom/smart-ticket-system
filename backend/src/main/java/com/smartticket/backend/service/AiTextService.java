package com.smartticket.backend.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Strictly returns final text. Model reasoning or malformed/empty output is never presented as an
 * answer.
 */
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class AiTextService {
  private final ChatClient chat;
  private final java.util.concurrent.Semaphore capacity = new java.util.concurrent.Semaphore(2);
  private final tools.jackson.databind.json.JsonMapper json =
      tools.jackson.databind.json.JsonMapper.builder().build();

  public AiTextService(ChatClient.Builder builder) {
    chat =
        builder
            .defaultOptions(
                OllamaChatOptions.builder()
                    .disableThinking()
                    .format("json")
                    .numCtx(8192)
                    .numPredict(768)
                    .temperature(0.2))
            .build();
  }

  public String generate(String system, String question) {
    if (!capacity.tryAcquire())
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI服务繁忙，请稍后重试");
    try {
      String raw =
          chat.prompt()
              .system(system + "\n只输出JSON对象，answer字段填写直接给用户看的最终答案或操作步骤。不要叙述分析和思考过程。")
              .user(question)
              .call()
              .content();
      String answer = raw == null ? "" : json.readTree(raw).path("answer").asText("");
      if (answer.isBlank())
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "模型未返回可读回答，请稍后重试");
      return answer;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI模型暂时不可用，请稍后重试");
    } finally {
      capacity.release();
    }
  }
}
