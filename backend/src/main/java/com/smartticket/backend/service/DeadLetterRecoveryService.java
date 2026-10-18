package com.smartticket.backend.service;

import com.smartticket.backend.config.RabbitMqConfig;
import com.smartticket.backend.dto.TicketCreatedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeadLetterRecoveryService {
  private final RabbitTemplate rabbit;
  private final OutboxService outbox;

  @Value("${app.mq.enabled:false}")
  private boolean enabled;

  public DeadLetterRecoveryService(RabbitTemplate rabbit, OutboxService outbox) {
    this.rabbit = rabbit;
    this.outbox = outbox;
  }

  public TicketCreatedEvent replayOne() {
    if (!enabled) throw new ResponseStatusException(HttpStatus.CONFLICT, "消息队列未启用");
    return rabbit.execute(
        channel -> {
          var delivery = channel.basicGet(RabbitMqConfig.TICKET_AUDIT_DLQ, false);
          if (delivery == null) return null;
          long tag = delivery.getEnvelope().getDeliveryTag();
          try {
            Message message =
                new Message(
                    delivery.getBody(),
                    new org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter()
                        .toMessageProperties(delivery.getProps(), delivery.getEnvelope(), "UTF-8"));
            Object decoded = rabbit.getMessageConverter().fromMessage(message);
            if (!(decoded instanceof TicketCreatedEvent event))
              throw new IllegalArgumentException("Unsupported dead letter");
            outbox.requeue(event);
            channel.basicAck(tag, false);
            return event;
          } catch (RuntimeException | java.io.IOException e) {
            channel.basicNack(tag, false, true);
            throw e;
          }
        });
  }
}
