#!/bin/bash
# set -x

cd analytics-layer

# Application variables
export PULSAR_SERVICE_URL=pulsar://localhost:6650

# OpenTelemetry variables
export OTEL_EXPORTER_OTLP_ENDPOINT=localhost:8200
export OTEL_RESOURCE_ATTRIBUTES=service.name=analytics-layer,service.version=1.0

# Run the analytics
go run analytics.go
