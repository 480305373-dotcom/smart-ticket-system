#!/bin/sh
set -eu
node_ip=$(hostname -i | awk '{print $1}')
if [ -s /data/nodes.conf ]; then
  # Resolve persisted peer hostnames before opening the cluster bus. Reused old
  # IPs can otherwise connect to a different peer during a simultaneous restart.
  umask 077
  : > /data/peer-addresses.tmp
  peers=$(awk 'length($1)==40 && NF>=8 {split($2,a,","); if(a[2]=="")exit 1; print a[2]}' /data/nodes.conf | sort -u)
  test -n "$peers"
  for peer in $peers; do
    peer_ip=""
    for attempt in $(seq 1 90); do
      peer_ip=$(getent hosts "$peer" | awk '$1 ~ /^[0-9]+\./ {print $1; exit}')
      if [ -n "$peer_ip" ]; then break; fi
      sleep 1
    done
    test -n "$peer_ip"
    printf '%s %s\n' "$peer" "$peer_ip" >> /data/peer-addresses.tmp
  done
  # Only replace address field 2; retain IDs, roles, epochs, shard metadata and slots.
  awk 'NR==FNR {ip[$1]=$2; next} length($1)==40 && NF>=8 {split($2,a,","); if(!(a[2] in ip))exit 1; sub(/^[^,]*/,ip[a[2]]":6379@16379",$2); gsub(/,noaddr/,"",$3); gsub(/^noaddr,/,"",$3)} {print}' \
    /data/peer-addresses.tmp /data/nodes.conf > /data/nodes.address-update.tmp
  cp /data/nodes.conf /data/nodes.before-address-update.conf
  mv /data/nodes.address-update.tmp /data/nodes.conf
  rm /data/peer-addresses.tmp
fi
exec redis-server --appendonly yes --cluster-enabled yes --cluster-config-file /data/nodes.conf \
  --cluster-node-timeout 5000 --cluster-announce-ip "$node_ip" --cluster-announce-hostname "$NODE_NAME" \
  --cluster-preferred-endpoint-type hostname --requirepass "$REDIS_PASSWORD" \
  --masterauth "$REDIS_PASSWORD" --maxmemory 96mb --maxmemory-policy noeviction
