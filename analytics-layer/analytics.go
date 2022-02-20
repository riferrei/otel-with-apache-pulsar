package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/apache/pulsar-client-go/pulsar"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/metric"
	"go.opentelemetry.io/otel/metric/global"
	"go.opentelemetry.io/otel/propagation"
	controller "go.opentelemetry.io/otel/sdk/metric/controller/basic"
	processor "go.opentelemetry.io/otel/sdk/metric/processor/basic"
	"go.opentelemetry.io/otel/sdk/metric/selector/simple"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.4.0"
	"go.opentelemetry.io/otel/trace"
)

const (
	metricPrefix    = "custom.metric."
	brandCountName  = metricPrefix + "brand.name"
	brandCountCount = metricPrefix + "brand.count"
	brandCountDesc  = "Count the number of estimates per brand"
	serviceName     = "analytics-layer"
	serviceVersion  = "1.0"
	topicName       = "estimates"
)

var (
	tracer trace.Tracer
	meter  metric.Meter
)

func main() {

	ctx := context.Background()

	endpoint := os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT")

	res0urce, err := resource.New(ctx,
		resource.WithAttributes(
			semconv.ServiceNameKey.String(serviceName),
			semconv.ServiceVersionKey.String(serviceVersion),
			semconv.TelemetrySDKVersionKey.String("v1.4.1"),
			semconv.TelemetrySDKLanguageGo))
	if err != nil {
		log.Fatalf("%s: %v", "failed to create resource", err)
	}

	// Initialize the tracer provider
	initTracer(ctx, endpoint, res0urce)

	// Initialize the meter provider
	initMeter(ctx, endpoint, res0urce)

	// Create the brand count metric
	brandCountMetric := metric.Must(meter).
		NewInt64Counter(
			brandCountCount,
			metric.WithDescription(brandCountDesc))

	// Consume messages from Pulsar
	client, err := pulsar.NewClient(pulsar.ClientOptions{
		URL:               os.Getenv("PULSAR_SERVICE_URL"),
		OperationTimeout:  30 * time.Second,
		ConnectionTimeout: 30 * time.Second,
	})
	if err != nil {
		log.Fatalf("Could not instantiate Pulsar client: %v", err)
	}
	defer client.Close()

	channel := make(chan pulsar.ConsumerMessage, 100)
	options := pulsar.ConsumerOptions{
		Topic:            topicName,
		SubscriptionName: serviceName,
		Type:             pulsar.Shared,
	}
	options.MessageChannel = channel

	consumer, err := client.Subscribe(options)
	if err != nil {
		log.Fatal(err)
	}
	defer consumer.Close()

	brandCount := make(map[string]int)

	for consumerMessage := range channel {

		message := consumerMessage.Message

		extractedContext := otel.GetTextMapPropagator().Extract(ctx, pulsarCarrier{message})
		_, receiveSpan := tracer.Start(extractedContext, topicName+" receive",
			trace.WithAttributes(
				semconv.MessagingSystemKey.String("pulsar"),
				semconv.MessagingDestinationKindKey.String("topic"),
				semconv.MessagingDestinationKey.String(topicName),
				semconv.MessagingOperationReceive,
			))

		estimate := struct {
			Brand string  `json:"brand"`
			Price float32 `json:"price"`
		}{}
		err := json.Unmarshal(message.Payload(), &estimate)

		if err == nil {

			count := brandCount[estimate.Brand]
			if count == 0 {
				count = 1
			} else {
				count++
			}
			brandCount[estimate.Brand] = count

			brandCountMetric.Add(ctx, 1,
				[]attribute.KeyValue{
					attribute.String(
						brandCountName,
						estimate.Brand),
				}...)

			fmt.Printf("Count for brand '%s': %d\n", estimate.Brand, brandCount[estimate.Brand])
			consumer.Ack(message)
			receiveSpan.End()

		}

	}

}

func initTracer(ctx context.Context, endpoint string,
	res0urce *resource.Resource) {

	traceOpts := []otlptracegrpc.Option{
		otlptracegrpc.WithEndpoint(endpoint),
		otlptracegrpc.WithInsecure(),
		otlptracegrpc.WithTimeout(5 * time.Second),
	}

	traceExporter, err := otlptracegrpc.New(ctx, traceOpts...)
	if err != nil {
		log.Fatalf("%s: %v", "failed to create exporter", err)
	}

	otel.SetTracerProvider(sdktrace.NewTracerProvider(
		sdktrace.WithSampler(sdktrace.AlwaysSample()),
		sdktrace.WithResource(res0urce),
		sdktrace.WithSpanProcessor(
			sdktrace.NewBatchSpanProcessor(traceExporter)),
	))

	otel.SetTextMapPropagator(
		propagation.NewCompositeTextMapPropagator(
			propagation.Baggage{},
			propagation.TraceContext{},
		),
	)

	tracer = otel.Tracer(serviceName)

}

func initMeter(ctx context.Context, endpoint string,
	res0urce *resource.Resource) {

	metricOpts := []otlpmetricgrpc.Option{
		otlpmetricgrpc.WithEndpoint(endpoint),
		otlpmetricgrpc.WithInsecure(),
		otlpmetricgrpc.WithTimeout(5 * time.Second),
	}

	metricExporter, err := otlpmetricgrpc.New(ctx, metricOpts...)
	if err != nil {
		log.Fatalf("%s: %v", "failed to create exporter", err)
	}

	pusher := controller.New(
		processor.NewFactory(
			simple.NewWithHistogramDistribution(),
			metricExporter,
		),
		controller.WithResource(res0urce),
		controller.WithExporter(metricExporter),
		controller.WithCollectPeriod(5*time.Second),
	)

	err = pusher.Start(ctx)
	if err != nil {
		log.Fatalf("%s: %v", "failed to start the controller", err)
	}

	global.SetMeterProvider(pusher)
	meter = global.Meter(serviceName)

}

type pulsarCarrier struct {
	Message pulsar.Message
}

func (pulsar pulsarCarrier) Get(key string) string {
	return pulsar.Message.Properties()[key]
}

func (pulsar pulsarCarrier) Set(key string, value string) {
	pulsar.Message.Properties()[key] = value
}

func (pulsar pulsarCarrier) Keys() []string {
	return []string{pulsar.Message.Key()}
}
