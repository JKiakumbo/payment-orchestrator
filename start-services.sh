#!/bin/bash

echo "Starting Payment Orchestration System..."

# Build all services
echo "Building services..."
./gradlew build -x test

# Start all services
echo "Starting Docker containers..."
docker compose up -d

echo "Waiting for services to start..."
sleep 30

# Check service status
echo "Service Status:"
echo "Orchestrator Service: $(curl -s http://localhost:8080/actuator/health | jq -r .status)"
echo "Fraud Service: $(curl -s http://localhost:8081/actuator/health | jq -r .status)"
echo "Funds Service: $(curl -s http://localhost:8082/actuator/health | jq -r .status)"
echo "Processor Service: $(curl -s http://localhost:8083/actuator/health | jq -r .status)"
echo "Ledger Service: $(curl -s http://localhost:8084/actuator/health | jq -r .status)"

echo ""
echo "Access URLs:"
echo "Orchestrator Service API: http://localhost:8080"
echo "Fraud Service API: http://localhost:8081"
echo "Funds Service API: http://localhost:8082"
echo "Processor Service API: http://localhost:8083"
echo "Ledger Service API: http://localhost:8084"
echo "Kafka UI: http://localhost:8085"
echo "Jaeger UI: http://localhost:16686"
echo "Grafana: http://localhost:3000 (admin/admin)"
echo "Prometheus: http://localhost:9090"