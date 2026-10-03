package com.smartticket.backend.controller;

import com.smartticket.backend.dto.ApiResponse;
import com.smartticket.backend.dto.TicketCreatedEvent;
import com.smartticket.backend.security.AuthUser;
import com.smartticket.backend.service.DeadLetterRecoveryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MqAdminController {

    private final DeadLetterRecoveryService deadLetterRecoveryService;

    public MqAdminController(
            DeadLetterRecoveryService deadLetterRecoveryService
    ) {
        this.deadLetterRecoveryService = deadLetterRecoveryService;
    }

    @PostMapping("/admin/mq/replay-dead-letter")
    public ApiResponse<TicketCreatedEvent> replayDeadLetter(
            Authentication authentication
    ) {

        AuthUser user =
                (AuthUser) authentication.getPrincipal();

        if (!"ADMIN".equals(user.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "只有管理员可以重放死信消息"
            );
        }

        TicketCreatedEvent event =
                deadLetterRecoveryService.replayOne();

        if (event == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "当前没有待处理的死信消息"
            );
        }

        return ApiResponse.success(event);
    }
}