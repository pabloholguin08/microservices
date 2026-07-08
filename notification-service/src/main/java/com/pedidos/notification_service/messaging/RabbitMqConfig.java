package com.pedidos.notification_service.messaging;

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

    public static final String ROUTING_KEY_ORDER_CONFIRMED = "order.confirmed";
    public static final String ROUTING_KEY_ORDER_CANCELLED = "order.cancelled";

    public static final String ORDER_CONFIRMED_QUEUE = "notification-service.order.confirmed";
    public static final String ORDER_CANCELLED_QUEUE = "notification-service.order.cancelled";

    @Bean
    public TopicExchange ordersExchange() {
        return ExchangeBuilder.topicExchange(ORDERS_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue orderConfirmedQueue() {
        return withDeadLetter(QueueBuilder.durable(ORDER_CONFIRMED_QUEUE), ORDER_CONFIRMED_QUEUE).build();
    }

    @Bean
    public Queue orderCancelledQueue() {
        return withDeadLetter(QueueBuilder.durable(ORDER_CANCELLED_QUEUE), ORDER_CANCELLED_QUEUE).build();
    }

    @Bean
    public Queue orderConfirmedDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_CONFIRMED_QUEUE + ".dlq").build();
    }

    @Bean
    public Queue orderCancelledDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_CANCELLED_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding orderConfirmedBinding(Queue orderConfirmedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(orderConfirmedQueue).to(ordersExchange).with(ROUTING_KEY_ORDER_CONFIRMED);
    }

    @Bean
    public Binding orderCancelledBinding(Queue orderCancelledQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(orderCancelledQueue).to(ordersExchange).with(ROUTING_KEY_ORDER_CANCELLED);
    }

    @Bean
    public Binding orderConfirmedDeadLetterBinding(Queue orderConfirmedDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(orderConfirmedDeadLetterQueue).to(deadLetterExchange).with(ORDER_CONFIRMED_QUEUE);
    }

    @Bean
    public Binding orderCancelledDeadLetterBinding(Queue orderCancelledDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(orderCancelledDeadLetterQueue).to(deadLetterExchange).with(ORDER_CANCELLED_QUEUE);
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
