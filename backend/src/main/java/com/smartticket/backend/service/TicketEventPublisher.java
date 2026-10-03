package com.smartticket.backend.service;

import com.smartticket.backend.config.RabbitMqConfig;
import com.smartticket.backend.dto.TicketCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class TicketEventPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final OperationLogService logs;

  @org.springframework.beans.factory.annotation.Value("${app.mq.enabled:true}")
  private boolean enabled;

  public TicketEventPublisher(RabbitTemplate rabbitTemplate, OperationLogService logs) {
    this.rabbitTemplate = rabbitTemplate;
    this.logs = logs;
  }

  public void publishTicketCreated(TicketCreatedEvent event) {
    if (!enabled) {
      logs.record(
          event.getUserId(), "CREATE_TICKET", event.getTicketId(), "创建工单，来源：" + event.getSource());
      return;
    }
    rabbitTemplate.convertAndSend(
        RabbitMqConfig.TICKET_EXCHANGE, RabbitMqConfig.TICKET_CREATED_ROUTING_KEY, event);
  }
}
