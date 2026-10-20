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
if redis-cli -h "$first" cluster info | grep -q 'cluster_slots_assigned:0'; then
  # Only create a fresh isolated node. Never reset a partially initialized cluster.
  if ! redis-cli -h "$first" cluster info | grep -q 'cluster_known_nodes:1'; then
    echo 'Existing incomplete cluster requires operator inspection; refusing to reset data.' >&2
    exit 1
  fi
  redis-cli --cluster create $hosts --cluster-replicas 1 --cluster-yes
else
  # Persisted nodes.conf contains old IPs after containers/Pods are recreated.
  # MEET refreshes peer addresses without deleting keys, IDs, roles or slot ownership.
  for i in 1 2 3 4 5; do
    host="redis-$i${REDIS_DNS_SUFFIX:-}"
    peer_ip=$(getent hosts "$host" | awk 'NR==1 {print $1}')
    test -n "$peer_ip"
    redis-cli -h "$first" cluster meet "$peer_ip" 6379
  done
fi

for attempt in $(seq 1 90); do
  ready=true
  topology=""
  for address in $hosts; do
    host=${address%:6379}
    if ! redis-cli -h "$host" cluster info | grep -q 'cluster_state:ok'; then ready=false; fi
    replication=$(redis-cli -h "$host" info replication)
    if printf '%s' "$replication" | grep -q '^role:slave'; then
      if ! printf '%s' "$replication" | grep -q '^master_link_status:up'; then ready=false; fi
    elif ! printf '%s' "$replication" | grep -Eq '^connected_slaves:[1-9]'; then ready=false
    fi
    # A connected replica is not enough: every node must agree on each primary's
    # epoch and slots before an automatic election can safely obtain votes.
    view=$(redis-cli -h "$host" cluster nodes | awk '$3 ~ /master/ {printf "%s %s ",$1,$7; for(i=9;i<=NF;i++)printf "%s ",$i; print ""}' | sort)
    if [ -z "$topology" ]; then topology=$view
    elif [ "$topology" != "$view" ]; then ready=false
    fi
  done
  if [ "$ready" = true ]; then echo 'Redis slots and every replication link are ready'; exit 0; fi
  sleep 2
done
echo 'Existing cluster did not recover; data and topology were preserved for inspection.' >&2
exit 1
