#!/bin/sh
set -e

mkdir -p /app/data

nginx -g 'daemon on;'

exec java -jar /app/app.jar
