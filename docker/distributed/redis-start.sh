#!/bin/sh
set -eu
node_ip=$(hostname -i | awk '{print $1}')
exec redis-server --appendonly yes --cluster-enabled yes --cluster-config-file /data/nodes.conf \
  --cluster-node-timeout 5000 --cluster-announce-ip "$node_ip" --cluster-announce-hostname "$NODE_NAME" \
  --cluster-preferred-endpoint-type hostname --requirepass "$REDIS_PASSWORD" \
  --masterauth "$REDIS_PASSWORD" --maxmemory 96mb --maxmemory-policy noeviction
