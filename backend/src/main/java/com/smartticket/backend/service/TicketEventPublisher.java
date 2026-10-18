package com.smartticket.backend.service;

import com.smartticket.backend.dto.TicketCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketEventPublisher {
  private final OutboxService outbox;
  private final OperationLogService logs;

  @Value("${app.mq.enabled:false}")
  private boolean enabled;

  public TicketEventPublisher(OutboxService outbox, OperationLogService logs) {
    this.outbox = outbox;
    this.logs = logs;
  }

  public void created(Long userId, Long ticketId) {
    publishTicketCreated(new TicketCreatedEvent(userId, ticketId, "NORMAL_CREATE"));
  }

  @Transactional
  public void publishTicketCreated(TicketCreatedEvent event) {
    if (enabled) outbox.enqueue(event);
    else
      logs.record(
          event.getUserId(), "CREATE_TICKET", event.getTicketId(), "创建工单，来源：" + event.getSource());
  }
}
