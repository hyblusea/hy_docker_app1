#!/bin/sh
set -e
BACKEND_URL=${BACKEND_URL:-http://backend:8080}
export BACKEND_URL
envsubst '${BACKEND_URL}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf
cat /etc/nginx/conf.d/default.conf
exec "$@"
