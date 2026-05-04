#!/bin/sh
set -e

mkdir -p /app/data

nginx
exec java -jar /app/app.jar
