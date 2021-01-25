#!/bin/bash
# set -x

# Run a clean build
go build -o analytics-layer/analytics-layer analytics-layer/analytics.go

# Application variables
export PULSAR_SERVICE_URL=pulsar://localhost:6650
export OTEL_EXPORTER_OTLP_ENDPOINT=localhost:55680

analytics-layer/analytics-layer
