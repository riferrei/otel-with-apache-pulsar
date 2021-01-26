#!/bin/bash
# set -x

# Run a clean build
go build -o analytics-layer/analytics-layer analytics-layer/analytics.go

# Application variables
export PULSAR_SERVICE_URL=pulsar://localhost:6650

# OpenTelemetry variables
export OTEL_EXPORTER_OTLP_ENDPOINT=localhost:55680
export OTEL_RESOURCE_ATTRIBUTES="service.name=analytics-layer,service.version=1.0"

analytics-layer/analytics-layer
