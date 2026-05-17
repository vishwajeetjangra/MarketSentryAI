# MarketSentry — Complete Project Implementation Plan

## Project Definition

MarketSentry is a distributed real-time trade surveillance platform that simulates streaming market trades, detects suspicious trading activity using stateful rule-based anomaly detection, and generates AI-assisted risk summaries using a local LLM.

The system is designed to resemble production-style fintech surveillance infrastructure used by investment banks, exchanges, hedge funds, and compliance teams.

The project focuses primarily on:
- Distributed systems
- Event-driven architecture
- Kafka streaming
- Stateful processing
- Backend scalability
- Asynchronous pipelines
- Observability
- AI-assisted alert enrichment

The AI is NOT responsible for primary anomaly detection.
The core detection logic remains deterministic, auditable, and rule-based.

---

# High Level System Architecture

```text
                    ┌────────────────────┐
                    │ Trade Generator    │
                    │ (Spring Boot)      │
                    └─────────┬──────────┘
                              │
                              ▼
                    ┌────────────────────┐
                    │ Kafka              │
                    │ trade-events topic │
                    └─────────┬──────────┘
                              │
                              ▼
                    ┌────────────────────┐
                    │ Surveillance Engine│
                    │ (Spring Boot)      │
                    └──────┬───────┬─────┘
                           │       │
                    Redis State    │
                           │       │
                           ▼       ▼
                    ┌──────────┐  Kafka Alert Topic
                    │ Redis    │         │
                    └──────────┘         ▼
                                  ┌───────────────┐
                                  │ AI Summarizer │
                                  │ (Spring Boot) │
                                  └──────┬────────┘
                                         │
                                         ▼
                                  ┌───────────────┐
                                  │ Ollama        │
                                  │ Local LLM     │
                                  └──────┬────────┘
                                         │
                                         ▼
                                  ┌───────────────┐
                                  │ PostgreSQL    │
                                  └───────────────┘
```

---

# Core System Flow

## Step 1 — Trade Generation
A mock market trade generator continuously creates randomized trade events.

Example trade:

```json
{
  "tradeId": "TRX1001",
  "traderId": "T2001",
  "stock": "AAPL",
  "side": "BUY",
  "quantity": 500,
  "price": 192.45,
  "timestamp": "2026-05-17T10:15:30"
}
```

These events are published to Kafka.

---

## Step 2 — Kafka Streaming
Kafka acts as the event streaming backbone.

Topics:

```text
trade-events
surveillance-alerts
ai-summaries
```

The Surveillance Engine consumes trade events asynchronously.

---

## Step 3 — Surveillance Processing
The Surveillance Engine:
- consumes trades
- fetches trader state from Redis
- updates rolling activity windows
- invokes the Rule Engine
- generates alerts
- persists data
- publishes alert events

---

## Step 4 — Redis Stateful Tracking
Redis stores temporary real-time trader activity.

Examples:

```text
Trader T1001
- trades last 1 minute = 42
- avg trade volume = 200
- current trade volume = 1500
- rapid buy/sell reversals = 6
```

This enables stateful anomaly detection.

---

## Step 5 — Rule Engine
The Rule Engine is an internal module inside the Surveillance Engine.

Responsibilities:
- evaluate suspicious trading patterns
- generate anomaly alerts
- assign risk severity

Example rules:

### High Frequency Spike
```text
If trader executes > 30 trades in 1 minute
→ suspicious
```

### Volume Spike
```text
If trade volume exceeds 5x trader average
→ suspicious
```

### Rapid Buy/Sell Reversal
```text
BUY followed immediately by SELL repeatedly
→ possible wash trading
```

### Price Deviation
```text
Trade price deviates heavily from market average
→ suspicious
```

---

## Step 6 — Alert Generation
When rules trigger, the system generates alerts.

Example:

```json
{
  "alertId": "ALT5001",
  "traderId": "T1001",
  "ruleTriggered": "HIGH_FREQUENCY_SPIKE",
  "severity": "HIGH",
  "message": "Trader executed 45 trades in 60 seconds",
  "timestamp": "2026-05-17T10:16:02"
}
```

These alerts are:
- persisted
- published to Kafka
- sent for AI summarization

---

## Step 7 — AI Summarization
The AI layer acts as a post-processing enrichment service.

It does NOT decide anomalies.

Responsibilities:
- generate human-readable summaries
- explain suspicious activity
- assist analysts

Example input:

```text
Trader executed 45 trades in 60 seconds.
Volume exceeded historical average by 700%.
Triggered rules:
- HIGH_FREQUENCY_SPIKE
- VOLUME_SPIKE
```

Example output:

```text
Potential spoofing-like behavior detected due to unusually high
trade frequency and abnormal trading volume.
```

---

# Repository Structure

```text
market-sentry/
│
├── trade-generator/
│
├── surveillance-engine/
│
├── ai-summarizer/
│
├── infrastructure/
│   ├── docker-compose.yml
│   ├── prometheus/
│   └── grafana/
│
├── docs/
│   ├── architecture.md
│   ├── api-docs.md
│   └── system-design.png
│
├── sample-data/
│
├── README.md
│
└── .gitignore
```

---

# Services Breakdown

# 1. Trade Generator Service

## Responsibilities
- generate randomized mock trades
- publish trade events to Kafka

## Tech
- Java 21
- Spring Boot
- Kafka Producer

## Features
- configurable trade rate
- randomized traders
- randomized stocks
- randomized quantities/prices
- configurable anomaly injection

---

# 2. Surveillance Engine

## Responsibilities
- consume trades from Kafka
- maintain trader state in Redis
- evaluate anomaly rules
- generate alerts
- expose APIs
- persist alerts

## Internal Modules

```text
surveillance-engine
│
├── kafka-consumer
├── trade-processor
├── redis-state-manager
├── rule-engine
├── alert-service
├── postgres-repository
└── api-layer
```

## Tech
- Java 21
- Spring Boot
- Spring WebFlux
- Redis
- PostgreSQL
- Kafka Consumer

---

# 3. AI Summarizer Service

## Responsibilities
- consume alert events
- generate AI summaries
- call local LLM via Ollama
- persist summaries

## Tech
- Java 21
- Spring Boot
- Ollama REST APIs

---

# Infrastructure Stack

| Component | Purpose |
|---|---|
| Kafka | Event streaming backbone |
| Redis | Stateful trader tracking |
| PostgreSQL | Persistent storage |
| Kafka UI | Kafka topic inspection |
| Ollama | Local LLM runtime |
| Prometheus | Metrics collection |
| Grafana | Monitoring dashboards |
| Docker Compose | Local distributed environment |

---

# Docker Services

```yaml
services:
  - kafka
  - zookeeper
  - kafka-ui
  - redis
  - postgres
  - ollama
  - trade-generator
  - surveillance-engine
  - ai-summarizer
```

---

# Database Design

# trades

| Column | Type |
|---|---|
| trade_id | VARCHAR |
| trader_id | VARCHAR |
| stock | VARCHAR |
| side | VARCHAR |
| quantity | BIGINT |
| price | DECIMAL |
| timestamp | TIMESTAMP |

---

# alerts

| Column | Type |
|---|---|
| alert_id | VARCHAR |
| trader_id | VARCHAR |
| rule_triggered | VARCHAR |
| severity | VARCHAR |
| message | TEXT |
| timestamp | TIMESTAMP |

---

# ai_summaries

| Column | Type |
|---|---|
| summary_id | VARCHAR |
| alert_id | VARCHAR |
| ai_summary | TEXT |
| created_at | TIMESTAMP |

---

# API Design

## Alerts

```text
GET /alerts
GET /alerts/high-risk
GET /alerts/{id}
```

---

## Traders

```text
GET /traders/{id}/risk
GET /traders/{id}/activity
```

---

## Statistics

```text
GET /stats
GET /stats/throughput
GET /stats/alerts
```

---

# Kafka Design

# Topics

## trade-events
Stores streaming trade events.

---

## surveillance-alerts
Stores suspicious activity alerts.

---

## ai-summaries
Stores AI-generated summaries.

---

# Scaling Considerations

## Kafka Partitions
Used to:
- scale consumers
- increase throughput
- parallelize processing

---

## Consumer Groups
Enable horizontal scaling of surveillance consumers.

---

## Dead Letter Queue
Failed events routed to DLQ.

Example:

```text
trade-events-dlq
```

---

## Retry Handling
Implement retry mechanisms for:
- transient failures
- DB connectivity issues
- AI service failures

---

# Monitoring & Observability

# Prometheus Metrics

Track:
- trades/sec
- alerts/sec
- API latency
- Kafka consumer lag
- Redis hit rates
- AI summarization latency

---

# Grafana Dashboards

Visualize:
- suspicious activity spikes
- top risky traders
- throughput metrics
- Kafka lag
- rule trigger frequency

---

# AI Design

## Ollama
Use local LLM inference.

Recommended model:

```text
mistral:7b
```

Alternative lightweight models:
- phi3
- gemma
- llama3

---

# AI Architecture

```text
Alert Generated
      ↓
Kafka Alert Topic
      ↓
AI Summarizer Service
      ↓
Ollama Local LLM
      ↓
AI Summary Stored
```

This keeps AI fully asynchronous and independently scalable.

---

# Recommended Development Timeline

# Week 1

## Goals
- create GitHub repo
- setup folder structure
- setup Docker Compose
- run Kafka
- run Redis
- run PostgreSQL
- run Kafka UI

---

# Week 2

## Goals
- build Trade Generator
- produce Kafka trade events
- verify Kafka streaming

---

# Week 3

## Goals
- build Surveillance Engine
- consume Kafka trades
- integrate Redis
- maintain trader state

---

# Week 4

## Goals
- implement Rule Engine
- generate alerts
- persist alerts to PostgreSQL

---

# Week 5

## Goals
- build AI Summarizer
- integrate Ollama
- generate AI summaries

---

# Week 6

## Goals
- add monitoring
- add Prometheus
- add Grafana
- implement retries and DLQ

---

# Week 7

## Goals
- improve architecture
- optimize Kafka consumers
- add screenshots
- improve README
- polish APIs

---

# Resume Positioning

Recommended resume description:

```text
Built a distributed real-time trade surveillance platform using Java, Spring WebFlux, Kafka, Redis, and PostgreSQL to process streaming market events and detect suspicious trading activity. Implemented stateful anomaly detection, asynchronous event pipelines, retry/DLQ handling, and AI-assisted alert summarization using local LLM inference.
```

---

# Key Engineering Concepts Demonstrated

- Distributed Systems
- Event-Driven Architecture
- Kafka Streaming
- Stateful Processing
- Redis Caching
- Asynchronous Pipelines
- Reactive Java
- Observability
- Fault Tolerance
- AI Enrichment Pipelines
- Dockerized Infrastructure
- Backend Scalability
- Production-Style Architecture

---

# Important Design Philosophy

The project should be positioned as:

```text
A distributed backend surveillance platform with AI-assisted enrichment.
```

NOT:

```text
An AI application that uses Kafka.
```

The distributed systems engineering should remain the primary focus of the project.

