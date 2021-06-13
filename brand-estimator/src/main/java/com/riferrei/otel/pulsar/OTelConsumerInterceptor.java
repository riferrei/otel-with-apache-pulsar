package com.riferrei.otel.pulsar;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerInterceptor;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

@SuppressWarnings("rawtypes")
public class OTelConsumerInterceptor implements ConsumerInterceptor {

    private final Tracer tracer =
        GlobalOpenTelemetry.getTracer(OTelConsumerInterceptor.class.getName(), "1.0");

    private final Map<MessageId, Span> cache = new ConcurrentHashMap<>();

    @Override
    public Message beforeConsume(Consumer consumer, Message message) {
        Context extractedContext = extractContextFromMessage(message);
        if (extractedContext != null) {
            String spanName = consumer.getTopic() + " receive";
            Span receiveSpan = tracer.spanBuilder(spanName)
                .setParent(extractedContext)
                .setSpanKind(SpanKind.CONSUMER)
                .startSpan();
            cache.put(message.getMessageId(), receiveSpan);
        }
        return message;
    }

    private Context extractContextFromMessage(Message message) {

        TextMapGetter<Message<?>> getter =
            new TextMapGetter<>(){

                @Override
                public String get(Message<?> message, String key) {
                    return message.getProperties().get(key);
                }
    
                @Override
                public Iterable<String> keys(Message<?> message) {
                    return message.getProperties().keySet();
                }
                
            };

        return GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
            .extract(Context.current(), message, getter);

    }

    @Override
    public void onAcknowledge(Consumer consumer, MessageId messageId, Throwable exception) {
        endSpan(consumer, messageId, exception, AcknowledgeType.Standard);
    }

    @Override
    public void onAcknowledgeCumulative(Consumer consumer, MessageId messageId, Throwable exception) {
        endSpan(consumer, messageId, exception, AcknowledgeType.Cumulative);
    }

    @Override
    public void onNegativeAcksSend(Consumer consumer, Set messageIds) {
        for (Object messageId : messageIds) {
            endSpan(consumer, (MessageId) messageId, null, AcknowledgeType.Negative);
        }
    }

    @Override
    public void onAckTimeoutSend(Consumer consumer, Set messageIds) {
        for (Object messageId : messageIds) {
            endSpan(consumer, (MessageId) messageId, null, AcknowledgeType.Timeout);
        }
    }

    private void endSpan(Consumer consumer, MessageId messageId,
        Throwable exception, AcknowledgeType acknowledgeType) {
        if (cache.containsKey(messageId)) {
            Span receiveSpan = cache.get(messageId);
            try {
                try (Scope scope = receiveSpan.makeCurrent()) {
                    receiveSpan.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "pulsar");
                    receiveSpan.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND,
                        SemanticAttributes.MessagingDestinationKindValues.TOPIC.getValue());
                    receiveSpan.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, consumer.getTopic());
                    receiveSpan.setAttribute("acknowledge.type", acknowledgeType.toString());
                    if (exception != null) {
                        receiveSpan.recordException(exception);
                    }
                } finally {
                    receiveSpan.end();
                }
            } finally {
                cache.remove(messageId);
            }
        }
    }

    private enum AcknowledgeType {
        Standard, Cumulative, Negative, Timeout
    }

    @Override
    public void close() {
    }

}
