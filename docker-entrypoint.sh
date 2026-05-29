#!/bin/sh
set -e

# K8s: JAVA_TOOL_OPTIONS 로 -Xmx·Metaspace 등 지정 (deployment/batch-deployment.yaml)
COMMON_OPTS="-XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom"

if [ "${SMW_GRAFANA_OTEL_ENABLED:-true}" = "true" ]; then
  exec java ${COMMON_OPTS} \
    -XX:MaxRAMPercentage=70.0 \
    -XX:InitialRAMPercentage=25.0 \
    -javaagent:grafana-opentelemetry-java.jar \
    -jar app.jar
fi

exec java ${COMMON_OPTS} -jar app.jar
