#!/bin/sh
set -eu
export REDISCLI_AUTH="$REDIS_PASSWORD"
hosts=""
for i in 0 1 2 3 4 5; do
  host="redis-$i${REDIS_DNS_SUFFIX:-}"
  until redis-cli -h "$host" ping | grep -q PONG; do sleep 2; done
  hosts="$hosts $host:6379"
done
first="redis-0${REDIS_DNS_SUFFIX:-}"
if redis-cli -h "$first" cluster info | grep -q 'cluster_state:ok'; then exit 0; fi
if ! redis-cli -h "$first" cluster info | grep -q 'cluster_slots_assigned:0'; then
  echo 'Existing incomplete cluster requires operator inspection; refusing to reset data.' >&2
  exit 1
fi
redis-cli --cluster create $hosts --cluster-replicas 1 --cluster-yes
