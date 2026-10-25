#!/bin/bash
set -e
KAFKA_BROKERS="kafka:29092"
echo "Initializing Kafka Topics..."
sleep 10
topics=(
  "trade-submitted"
  "trade-validated"
  "trade-processed"
  "position-updated"
  "settlement-completed"
  "notification-events"
  "reference-data-events"
  "corporate-action-events"
  "trade-dead-letter"
  "margin-call-issued"
)
for topic in "${topics[@]}"; do
  kafka-topics --create --if-not-exists --bootstrap-server "$KAFKA_BROKERS" --replication-factor 1 --partitions 3 --topic "$topic"
done
echo "All topics initialized."
