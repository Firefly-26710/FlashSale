#!/bin/sh
set -e

NACOS_ADDR="${NACOS_SERVER_ADDR:-nacos:8848}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
CONFIG_DIR="/nacos-config"

echo "[nacos-init] waiting for nacos at ${NACOS_ADDR}"
until curl -fsS "http://${NACOS_ADDR}/nacos/v1/console/health/readiness" >/dev/null; do
  sleep 2
done

echo "[nacos-init] start publishing configs"
for file in "${CONFIG_DIR}"/*.properties; do
  [ -f "$file" ] || continue
  dataId=$(basename "$file")
  content=$(cat "$file")
  result=$(curl -fsS -X POST "http://${NACOS_ADDR}/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${dataId}" \
    --data-urlencode "group=${NACOS_GROUP}" \
    --data-urlencode "content=${content}")
  echo "[nacos-init] publish ${dataId}: ${result}"
done

echo "[nacos-init] done"
