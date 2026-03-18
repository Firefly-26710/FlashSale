#!/bin/sh
set -eu

PRIMARY_HOST=${PRIMARY_HOST:-mysql-primary}
PRIMARY_PORT=${PRIMARY_PORT:-3306}
REPLICA_HOST=${REPLICA_HOST:-mysql-replica}
REPLICA_PORT=${REPLICA_PORT:-3306}
ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-root123}
REPL_USER=${REPL_USER:-repl}
REPL_PASSWORD=${REPL_PASSWORD:-repl123}

wait_for_mysql() {
  host="$1"
  port="$2"
  until mysql -h"$host" -P"$port" -uroot -p"$ROOT_PASSWORD" -e "SELECT 1" >/dev/null 2>&1; do
    echo "Waiting for MySQL at $host:$port ..."
    sleep 2
  done
}

wait_for_mysql "$PRIMARY_HOST" "$PRIMARY_PORT"
wait_for_mysql "$REPLICA_HOST" "$REPLICA_PORT"

CHANGE_STMT="CHANGE REPLICATION SOURCE TO \
  SOURCE_HOST='${PRIMARY_HOST}', \
  SOURCE_PORT=${PRIMARY_PORT}, \
  SOURCE_USER='${REPL_USER}', \
  SOURCE_PASSWORD='${REPL_PASSWORD}', \
  SOURCE_AUTO_POSITION=1, \
  GET_SOURCE_PUBLIC_KEY=1"

mysql -h"$REPLICA_HOST" -P"$REPLICA_PORT" -uroot -p"$ROOT_PASSWORD" -e "STOP REPLICA; RESET REPLICA ALL; ${CHANGE_STMT}; START REPLICA; SET GLOBAL read_only=ON; SET GLOBAL super_read_only=ON;"

echo "Replica replication configured."
mysql -h"$REPLICA_HOST" -P"$REPLICA_PORT" -uroot -p"$ROOT_PASSWORD" -e "SHOW REPLICA STATUS\\G" || true
