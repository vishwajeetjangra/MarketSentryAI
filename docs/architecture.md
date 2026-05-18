# MarketSentryAI — Architecture

## Overview

MarketSentryAI is a distributed real-time trade surveillance platform. The system processes streaming market trades, detects suspicious activity using stateful rule-based anomaly detection, and enriches alerts with AI-generated summaries via a local LLM.

---

## System Architecture

```
Trade Generator (8081)
      │
      ▼ [Kafka: trade-events]
Surveillance Engine (8082)
      │               │
   Redis State    [Kafka: surveillance-alerts]
                       │
                  AI Summarizer (8083)
                       │
                    Ollama (11434)
                       │
                  PostgreSQL (5432)
```

---

## Services

| Service              | Port | Tech Stack                                      |
|----------------------|------|-------------------------------------------------|
| trade-generator      | 8081 | Java 21, Spring Boot, Kafka Producer            |
| surveillance-engine  | 8082 | Java 21, Spring WebFlux, Kafka, Redis, Postgres |
| ai-summarizer        | 8083 | Java 21, Spring Boot, Kafka, Ollama, Postgres   |

---

## Infrastructure

| Component   | Port  | Purpose                       |
|-------------|-------|-------------------------------|
| Kafka       | 9092  | Event streaming backbone      |
| Kafka UI    | 8090  | Kafka topic inspection        |
| Redis       | 6379  | Stateful trader tracking      |
| PostgreSQL  | 5432  | Persistent alert/trade storage|
| Ollama      | 11434 | Local LLM inference           |
| Prometheus  | 9090  | Metrics collection            |
| Grafana     | 3000  | Monitoring dashboards         |

---

## Kafka Topics

| Topic                | Producer             | Consumer            |
|----------------------|----------------------|---------------------|
| trade-events         | trade-generator      | surveillance-engine |
| surveillance-alerts  | surveillance-engine  | ai-summarizer       |

---

## Anomaly Detection Rules

| Rule                    | Condition                                      | Severity |
|-------------------------|------------------------------------------------|----------|
| HIGH_FREQUENCY_SPIKE    | > 30 trades in 60 seconds                     | HIGH     |
| VOLUME_SPIKE            | Trade volume > 5x trader average              | MEDIUM   |
| RAPID_BUY_SELL_REVERSAL | 5+ buy/sell reversals within 10 seconds       | HIGH     |

---

## Data Flow

1. Trade Generator publishes randomized `TradeEvent` objects to `trade-events`
2. Surveillance Engine consumes trades, updates Redis state per trader
3. Rule Engine evaluates stateful conditions and emits `Alert` objects
4. Alerts are persisted to PostgreSQL and published to `surveillance-alerts`
5. AI Summarizer consumes alerts, calls Ollama, persists `AiSummary` to PostgreSQL
