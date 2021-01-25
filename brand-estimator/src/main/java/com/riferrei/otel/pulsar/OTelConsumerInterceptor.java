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
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

@SuppressWarnings("rawtypes")
public class OTelConsumerInterceptor implements ConsumerInterceptor {

    private final Tracer tracer =
        GlobalOpenTelemetry.getTracer(OTelConsumerInterceptor.class.getName(), "1.0");

    private final Map<MessageId, Context> cache = new ConcurrentHashMap<>();

    @Override
    public Message beforeConsume(Consumer consumer, Message message) {
        Context extractedContext = extractContextFromMessage(message);
        if (extractedContext != null) {
            cache.put(message.getMessageId(), extractedContext);
        }
        return message;
    }

    private Context extractContextFromMessage(Message message) {

        TextMapPropagator.Getter<Message<?>> getter =
            new TextMapPropagator.Getter<Message<?>>() {

                @Override
                public Iterable<String> keys(Message<?> message) {
                    return message.getProperties().keySet();
                }

                @Override
                public String get(Message<?> message, String key) {
                    return message.getProperties().get(key);
                }
                
            };

        return GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
            .extract(Context.current(), message, getter);

    }

    @Override
    public void onAcknowledge(Consumer consumer, MessageId messageId, Throwable exception) {
        if (cache.containsKey(messageId)) {
            try {
                Context extractedContext = cache.get(messageId);
                String spanName = consumer.getTopic() + " receive";
                Span receiveSpan = tracer.spanBuilder(spanName)
                    .setParent(extractedContext)
                    .setSpanKind(Span.Kind.CONSUMER)
                    .startSpan();
                try (Scope scope = receiveSpan.makeCurrent()) {
                    receiveSpan.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "pulsar");
                    receiveSpan.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic");
                    receiveSpan.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, consumer.getTopic());
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

    @Override
    public void onAcknowledgeCumulative(Consumer consumer, MessageId messageId, Throwable exception) {
    }

    @Override
    public void onNegativeAcksSend(Consumer consumer, Set messageIds) {
    }

    @Override
    public void onAckTimeoutSend(Consumer consumer, Set messageIds) {
    }

    @Override
    public void close() {
    }
    
}
