package com.smartticket.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

  public static final String TICKET_EXCHANGE = "smartticket.ticket.exchange";

  public static final String TICKET_AUDIT_QUEUE = "smartticket.ticket.audit.queue";

  public static final String TICKET_CREATED_ROUTING_KEY = "ticket.created";

  public static final String TICKET_DLX = "smartticket.ticket.dlx";

  public static final String TICKET_AUDIT_DLQ = "smartticket.ticket.audit.dlq";

  public static final String TICKET_DEAD_ROUTING_KEY = "ticket.audit.dead";

  @Bean
  public DirectExchange ticketExchange() {
    return new DirectExchange(TICKET_EXCHANGE);
  }

  @Bean
  public DirectExchange ticketDeadLetterExchange() {
    return new DirectExchange(TICKET_DLX);
  }

  @Bean
  public Queue ticketAuditQueue() {
    return QueueBuilder.durable(TICKET_AUDIT_QUEUE)
        .deadLetterExchange(TICKET_DLX)
        .deadLetterRoutingKey(TICKET_DEAD_ROUTING_KEY)
        .build();
  }

  @Bean
  public Queue ticketAuditDeadLetterQueue() {
    return QueueBuilder.durable(TICKET_AUDIT_DLQ).build();
  }

  @Bean
  public Binding ticketAuditBinding(
      @Qualifier("ticketAuditQueue") Queue ticketAuditQueue,
      @Qualifier("ticketExchange") DirectExchange ticketExchange) {
    return BindingBuilder.bind(ticketAuditQueue)
        .to(ticketExchange)
        .with(TICKET_CREATED_ROUTING_KEY);
  }

  @Bean
  public Binding ticketDeadLetterBinding(
      @Qualifier("ticketAuditDeadLetterQueue") Queue ticketAuditDeadLetterQueue,
      @Qualifier("ticketDeadLetterExchange") DirectExchange ticketDeadLetterExchange) {
    return BindingBuilder.bind(ticketAuditDeadLetterQueue)
        .to(ticketDeadLetterExchange)
        .with(TICKET_DEAD_ROUTING_KEY);
  }

  @Bean
  public MessageConverter messageConverter() {
    return new JacksonJsonMessageConverter("com.smartticket.backend.dto");
  }
}
