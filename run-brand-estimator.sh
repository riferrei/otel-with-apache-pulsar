#!/bin/bash
# set -x

# Run a clean build
mvn -f brand-estimator/pom.xml clean package -Dmaven.test.skip=true

# Download the agent
AGENT_FILE=opentelemetry-javaagent-all.jar
LATEST_AGENT_URL=https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.11.0/opentelemetry-javaagent.jar
if [ ! -f "${AGENT_FILE}" ]; then
  curl -L ${LATEST_AGENT_URL} --output ${AGENT_FILE}
fi

# Application variables
export REDIS_HOSTNAME=localhost
export REDIS_PORT=6379
export PULSAR_SERVICE_URL=pulsar://localhost:6650

# OpenTelemetry variables
export OTEL_EXPORTER=otlp
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:8200
export OTEL_RESOURCE_ATTRIBUTES=service.name=brand-estimator,service.version=1.0

java -javaagent:./${AGENT_FILE} -jar brand-estimator/target/brand-estimator-1.0.jar
