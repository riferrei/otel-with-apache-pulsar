package com.riferrei.otel.pulsar;

import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.interceptor.ProducerInterceptor;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.client.impl.TopicMessageImpl;
import org.apache.pulsar.common.api.proto.PulsarApi;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

@SuppressWarnings("rawtypes")
public class OTelProducerInterceptor implements ProducerInterceptor {

    private final Tracer tracer =
        GlobalOpenTelemetry.getTracer(OTelProducerInterceptor.class.getName(), "1.0");

    @Override
    public boolean eligible(Message message) {
        return true;
    }

    @Override
    public Message<?> beforeSend(Producer producer, Message message) {

        String spanName = producer.getTopic() + " send";
        Span sendSpan = tracer.spanBuilder(spanName)
            .setSpanKind(Span.Kind.PRODUCER)
            .startSpan();

        Context newContext = Context.current().with(sendSpan);

        try (Scope scope = newContext.makeCurrent()) {
            sendSpan.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "pulsar");
            sendSpan.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND,
                SemanticAttributes.MessagingDestinationKindValues.TOPIC.getValue());
            sendSpan.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, producer.getTopic());
            storeContextOnMessage(newContext, message);
        } finally {
            sendSpan.end();
        }

        return message;
    }

    private void storeContextOnMessage(Context context, Message<?> message) {

        TextMapPropagator.Setter<Message<?>> setter =
            new TextMapPropagator.Setter<Message<?>>() {

            @Override
            public void set(Message<?> message, String key, String value) {
                MessageImpl<?> msg = null;
                if (message instanceof MessageImpl<?>) {
                    msg = (MessageImpl<?>) message;
                } else if (message instanceof TopicMessageImpl<?>) {
                    msg = (MessageImpl<?>) ((TopicMessageImpl<?>) message).getMessage();
                }
                if (msg != null) {
                    msg.getMessageBuilder().addProperties(
                        PulsarApi.KeyValue.newBuilder()
                        .setKey(key).setValue(value));
                }
            }

        };

        GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
            .inject(context, message, setter);

    }

    @Override
    public void onSendAcknowledgement(Producer producer, Message message,
        MessageId msgId, Throwable exception) {
    }

    @Override
    public void close() {
    }

}
