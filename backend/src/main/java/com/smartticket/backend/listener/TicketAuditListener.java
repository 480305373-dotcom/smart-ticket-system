package com.smartticket.backend.listener;

import com.smartticket.backend.config.RabbitMqConfig;
import com.smartticket.backend.dto.TicketCreatedEvent;
import com.smartticket.backend.service.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TicketAuditListener {
  private final OperationLogService logs;
  private final RabbitMqIdempotencyService inbox;

  public TicketAuditListener(OperationLogService logs, RabbitMqIdempotencyService inbox) {
    this.logs = logs;
    this.inbox = inbox;
  }

  @Transactional
  @RabbitListener(queues = RabbitMqConfig.TICKET_AUDIT_QUEUE)
  public void handleTicketCreated(TicketCreatedEvent event) {
    if (event.getUserId() == null || event.getTicketId() == null)
      throw new IllegalArgumentException("Invalid ticket event");
    String id = event.getEventId();
    if (id == null || id.isBlank()) id = "ticket-created-" + event.getTicketId();
    if (id.length() > 100) throw new IllegalArgumentException("Invalid event identifier");
    if (!inbox.tryAcquire(id)) return;
    logs.record(
        event.getUserId(),
        "CREATE_TICKET_ASYNC",
        event.getTicketId(),
        "异步创建审计，来源：" + event.getSource() + "，eventId：" + id);
  }
}
