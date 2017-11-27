#!/usr/bin/env bash
HTTPBACKEND_HOST=localhost \
HTTPBACKEND_PORT=9090 \
PORT=9095 \
SERVICE_PORT=9095 \
java -jar target/simple-microservice-1.0-SNAPSHOT-fat.jar