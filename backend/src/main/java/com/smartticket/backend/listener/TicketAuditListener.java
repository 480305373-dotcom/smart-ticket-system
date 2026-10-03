package com.smartticket.backend.listener;

import com.smartticket.backend.config.RabbitMqConfig;
import com.smartticket.backend.dto.TicketCreatedEvent;
import com.smartticket.backend.service.OperationLogService;
import com.smartticket.backend.service.RabbitMqIdempotencyService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TicketAuditListener {

    private final OperationLogService operationLogService;
    private final RabbitMqIdempotencyService idempotencyService;

    public TicketAuditListener(
            OperationLogService operationLogService,
            RabbitMqIdempotencyService idempotencyService
    ) {
        this.operationLogService = operationLogService;
        this.idempotencyService = idempotencyService;
    }

    @RabbitListener(
            queues = RabbitMqConfig.TICKET_AUDIT_QUEUE
    )
    public void handleTicketCreated(
            TicketCreatedEvent event
    ) {

        String eventId = event.getEventId();

        // 兼容之前没有 eventId 的旧消息
        if (eventId == null || eventId.isBlank()) {
            eventId = "ticket-created-"
                    + event.getTicketId();
        }

        // Redis SETNX
        if (!idempotencyService.tryAcquire(eventId)) {

            System.out.println(
                    "检测到RabbitMQ重复消息，跳过处理，eventId = "
                            + eventId
            );

            return;
        }

        try {

            operationLogService.record(
                    event.getUserId(),
                    "CREATE_TICKET_ASYNC",
                    event.getTicketId(),
                    "RabbitMQ异步记录工单创建，来源："
                            + event.getSource()
                            + "，eventId："
                            + eventId
            );

            System.out.println(
                    "RabbitMQ消息消费成功，eventId = "
                            + eventId
            );

        } catch (Exception e) {

            // 业务失败，删掉幂等标记
            // 这样 RabbitMQ 重试时还能继续处理
            idempotencyService.release(eventId);

            throw e;
        }
    }
}