#!/bin/bash
# set -x

# Run a clean build
mvn -f brand-estimator/pom.xml clean package -Dmaven.test.skip=true

# Download the agent
AGENT_FILE=opentelemetry-javaagent-all.jar
LATEST_AGENT_URL=https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent-all.jar
if [ ! -f "${AGENT_FILE}" ]; then
  wget -O ${AGENT_FILE} ${LATEST_AGENT_URL}
fi

# Application variables
export REDIS_HOSTNAME=localhost
export REDIS_PORT=6379
export PULSAR_SERVICE_URL=pulsar://localhost:6650

# OpenTelemetry variables
export OTEL_EXPORTER=otlp
export OTEL_EXPORTER_OTLP_ENDPOINT=localhost:55680
export OTEL_RESOURCE_ATTRIBUTES=service.name=brand-estimator,service.version=1.0

java -javaagent:./${AGENT_FILE} -jar brand-estimator/target/brand-estimator-0.1.0.jar
