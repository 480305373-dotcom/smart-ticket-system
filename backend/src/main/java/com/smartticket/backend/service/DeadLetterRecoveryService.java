package com.smartticket.backend.service;

import com.smartticket.backend.config.RabbitMqConfig;
import com.smartticket.backend.dto.TicketCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class DeadLetterRecoveryService {

    private final RabbitTemplate rabbitTemplate;

    public DeadLetterRecoveryService(
            RabbitTemplate rabbitTemplate
    ) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 从死信队列取出一条消息，
     * 重新发送到正常业务交换机。
     */
    public TicketCreatedEvent replayOne() {

        Object message =
                rabbitTemplate.receiveAndConvert(
                        RabbitMqConfig.TICKET_AUDIT_DLQ,
                        1000
                );

        // DLQ 当前没有消息
        if (message == null) {
            return null;
        }

        // 防止错误类型消息被重放
        if (!(message instanceof TicketCreatedEvent event)) {
            throw new IllegalStateException(
                    "死信消息类型异常"
            );
        }

        try {

            // 重新发送回正常 Exchange
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.TICKET_EXCHANGE,
                    RabbitMqConfig.TICKET_CREATED_ROUTING_KEY,
                    event
            );

            return event;

        } catch (RuntimeException e) {

            // 如果重新发送失败，
            // 再放回死信交换机，避免直接丢失
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.TICKET_DLX,
                    RabbitMqConfig.TICKET_DEAD_ROUTING_KEY,
                    event
            );

            throw e;
        }
    }
}