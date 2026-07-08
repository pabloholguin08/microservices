package com.pedidos.orders_service.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitMqConfig {

    public static final String ORDERS_EXCHANGE = "orders.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "orders.dlx";

    public static final String ROUTING_KEY_ORDER_CREATED = "order.created";
    public static final String ROUTING_KEY_ORDER_CONFIRMED = "order.confirmed";
    public static final String ROUTING_KEY_ORDER_CANCELLED = "order.cancelled";
    public static final String ROUTING_KEY_STOCK_RESERVED = "order.stock.reserved";
    public static final String ROUTING_KEY_STOCK_REJECTED = "order.stock.rejected";

    public static final String STOCK_RESERVED_QUEUE = "orders-service.order.stock.reserved";
    public static final String STOCK_REJECTED_QUEUE = "orders-service.order.stock.rejected";

    @Bean
    public TopicExchange ordersExchange() {
        return ExchangeBuilder.topicExchange(ORDERS_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue stockReservedQueue() {
        return withDeadLetter(QueueBuilder.durable(STOCK_RESERVED_QUEUE), STOCK_RESERVED_QUEUE).build();
    }

    @Bean
    public Queue stockRejectedQueue() {
        return withDeadLetter(QueueBuilder.durable(STOCK_REJECTED_QUEUE), STOCK_REJECTED_QUEUE).build();
    }

    @Bean
    public Queue stockReservedDeadLetterQueue() {
        return QueueBuilder.durable(STOCK_RESERVED_QUEUE + ".dlq").build();
    }

    @Bean
    public Queue stockRejectedDeadLetterQueue() {
        return QueueBuilder.durable(STOCK_REJECTED_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding stockReservedBinding(Queue stockReservedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(stockReservedQueue).to(ordersExchange).with(ROUTING_KEY_STOCK_RESERVED);
    }

    @Bean
    public Binding stockRejectedBinding(Queue stockRejectedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(stockRejectedQueue).to(ordersExchange).with(ROUTING_KEY_STOCK_REJECTED);
    }

    @Bean
    public Binding stockReservedDeadLetterBinding(Queue stockReservedDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(stockReservedDeadLetterQueue).to(deadLetterExchange).with(STOCK_RESERVED_QUEUE);
    }

    @Bean
    public Binding stockRejectedDeadLetterBinding(Queue stockRejectedDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(stockRejectedDeadLetterQueue).to(deadLetterExchange).with(STOCK_REJECTED_QUEUE);
    }

    private static QueueBuilder withDeadLetter(QueueBuilder builder, String queueName) {
        return builder
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", queueName);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    /**
     * Retries a failing listener invocation 3 times with backoff before rejecting the message
     * without requeueing, which routes it to the queue's dead-letter queue instead of looping
     * forever.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(3)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
