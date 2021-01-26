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
	"go.opentelemetry.io/otel/exporters/otlp"
	"go.opentelemetry.io/otel/exporters/otlp/otlpgrpc"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/semconv"
	"go.opentelemetry.io/otel/trace"
)

const (
	serviceName    = "analytics-layer"
	serviceVersion = "1.0"
	topicName      = "estimates"
)

func main() {

	/***************************************************/
	/****** Initialize a new OpenTelemetry tracer ******/
	/***************************************************/

	ctx := context.Background()

	driver := otlpgrpc.NewDriver(
		otlpgrpc.WithInsecure(),
		otlpgrpc.WithEndpoint(os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT")),
	)
	exporter, err := otlp.NewExporter(ctx, driver)
	if err != nil {
		log.Fatalf("%s: %v", "failed to create exporter", err)
	}

	res, err := resource.New(ctx,
		resource.WithAttributes(
			semconv.TelemetrySDKNameKey.String("opentelemetry"),
			semconv.TelemetrySDKLanguageKey.String("go"),
			semconv.TelemetrySDKVersionKey.String("1.15.6")))

	bsp := sdktrace.NewBatchSpanProcessor(exporter)
	defer bsp.Shutdown(ctx)

	tracerProvider := sdktrace.NewTracerProvider(
		sdktrace.WithSpanProcessor(bsp),
		sdktrace.WithResource(res))

	otel.SetTracerProvider(tracerProvider)
	otel.SetTextMapPropagator(propagation.TraceContext{})
	tracer := otel.Tracer(serviceName)

	/***************************************************/
	/***** Connect with Pulsar to process messages *****/
	/***************************************************/

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

		extractedContext := otel.GetTextMapPropagator().Extract(ctx, PulsarCarrier{message})
		_, receiveSpan := tracer.Start(extractedContext, topicName+" receive",
			trace.WithAttributes(
				semconv.MessagingSystemKey.String("pulsar"),
				semconv.MessagingDestinationKindKey.String("topic"),
				semconv.MessagingDestinationKey.String(topicName),
			))

		var estimate Estimate
		err := json.Unmarshal(message.Payload(), &estimate)
		if err == nil {
			count := brandCount[estimate.Brand]
			if count == 0 {
				count = 1
			} else {
				count = count + 1
			}
			brandCount[estimate.Brand] = count
			fmt.Printf("Count for brand '%s': %d\n", estimate.Brand, brandCount[estimate.Brand])
			consumer.Ack(message)
			receiveSpan.End()
		}

	}

}

// Estimate type
type Estimate struct {
	Brand string  `json:"brand"`
	Price float32 `json:"price"`
}

// PulsarCarrier type
type PulsarCarrier struct {
	Message pulsar.Message
}

// Get returns the value associated with the passed key.
func (pulsar PulsarCarrier) Get(key string) string {
	return pulsar.Message.Properties()[key]
}

// Set stores the key-value pair.
func (pulsar PulsarCarrier) Set(key string, value string) {
	pulsar.Message.Properties()[key] = value
}
