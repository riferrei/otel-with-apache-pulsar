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
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

@SuppressWarnings("rawtypes")
public class OTelProducerInterceptor implements ProducerInterceptor {

    @Override
    public boolean eligible(Message message) {
        return true;
    }

    @Override
    public Message<?> beforeSend(Producer producer, Message message) {
        return message;
    }

    @Override
    public void onSendAcknowledgement(Producer producer, Message message,
        MessageId msgId, Throwable exception) {
    }

    @Override
    public void close() {
    }

}
