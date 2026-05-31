# MarketSentryAI

A distributed trade surveillance pipeline that ingests a stream of trades, detects suspicious patterns in real time, and produces a plain-English explanation of each alert using a locally-hosted LLM.

Three Spring Boot services, Kafka as the event backbone, Redis for hot state, Postgres for the audit log, and Ollama for the summarization model. Built as a portfolio project to explore distributed systems patterns — Kafka consumer groups, idempotent processing, sliding windows, pluggable rules, cooldown gating, dead-letter queues, and Prometheus/Grafana observability.

---

## Architecture

```
                ┌──────────────────────┐
                │   trade-generator    │  REST control:  /generator/{start,stop,inject/...}
                │      (port 8081)     │
                └──────────┬───────────┘
                           │ publishes to
                           ▼
                     ┌──────────────┐
                     │    Kafka     │   topic: trade-events
                     └──────┬───────┘
                            │
                            ▼
                ┌──────────────────────┐       ┌──────────┐
                │ surveillance-engine  │◄─────►│  Redis   │  trader state, sliding windows, dedup
                │      (port 8082)     │       └──────────┘
                │                      │
                │  RuleEngine          │       ┌──────────┐
                │   • HIGH_FREQUENCY   │──────►│ Postgres │  trades + alerts
                │   • VOLUME_SPIKE     │       └──────────┘
                │   • RAPID_REVERSAL   │
                └──────────┬───────────┘
                           │ publishes alerts to
                           ▼
                     ┌──────────────┐
                     │    Kafka     │   topic: surveillance-alerts
                     └──────┬───────┘
                            │
                            ▼
                ┌──────────────────────┐       ┌──────────┐
                │    ai-summarizer     │──────►│  Ollama  │  phi3:mini (or mistral:7b)
                │      (port 8083)     │◄──────│ (:11434) │
                │                      │       └──────────┘
                │                      │       ┌──────────┐
                │                      │──────►│ Postgres │  ai_summaries
                └──────────────────────┘       └──────────┘
```

## Services

| Service | Port | Role |
|---|---|---|
| **trade-generator** | 8081 | Produces a steady stream of trades with random-walk pricing. REST endpoints to start/stop, change rate, or inject specific anomalies on demand. |
| **surveillance-engine** | 8082 | Consumes trades, maintains per-trader state in Redis (EMA volume baseline, 60s sliding window, 10s reversal window), runs each trade through the rule plugins, and publishes alerts to Kafka. |
| **ai-summarizer** | 8083 | Consumes alerts, calls the local LLM with a structured prompt, and persists the response to Postgres. |

## Tech Stack

- **Java 21**, Spring Boot 3.3.5
- **Kafka** (Confluent 7.6.0) — event bus between services
- **Redis 7.2** — hot state: trader profiles, sliding windows, idempotency keys, alert cooldowns
- **Postgres 16** — audit log: trades, alerts, AI summaries
- **Ollama** with `phi3:mini` (or `mistral:7b`) — locally-hosted LLM for alert summarization
- **Prometheus + Grafana** — metrics scraping and dashboards
- **Testcontainers** for Redis integration tests

## Anomaly Rules

Each rule is its own Spring bean implementing `Rule`. The `RuleEngine` discovers all `Rule` beans at startup and evaluates each trade against the full set. Adding a new rule means dropping in one new class.

| Rule | Trigger | Severity |
|---|---|---|
| `HIGH_FREQUENCY_SPIKE` | > 30 trades in 60s for one trader | HIGH |
| `VOLUME_SPIKE` | trade size > 5× the trader's EMA baseline (after 10-trade warmup) | MEDIUM |
| `RAPID_BUY_SELL_REVERSAL` | ≥ 5 side flips in 10s — possible wash trading | HIGH |

Thresholds live in `application.yml` under `marketsentry.surveillance.rules` and bind via `@ConfigurationProperties`, so they can be tuned without rebuilds.

---

## Notable engineering decisions

These are the parts that took real thought.

### Idempotent at-least-once processing

Kafka guarantees at-least-once delivery. A naive consumer that updates an EMA on every message would double-apply the math on every redelivery. Each trade is gated by a Redis `SETNX` claim on `trade:seen:{tradeId}` with a 10-minute TTL — first delivery wins, redeliveries no-op. Same pattern in the ai-summarizer for `summary:claim:{alertId}` so the LLM isn't called twice for the same alert.

### Sliding windows via Redis sorted sets

Trade-rate and reversal counts are stored as ZSETs of `(eventId, timestamp)`. Counting trades in the last 60s is a `ZCOUNT` range query; stale entries are pruned on read. The data structure decays itself — no background cleanup job, no counter that needs manual reset. The earlier implementation kept a counter on the trader-state object that could never decay, leading to permanent alert storms.

### Per-(trader, rule) alert cooldown

Without this, an ongoing violation fires one alert per trade — instant flood. A Redis `SETNX` on `alert:cooldown:{traderId}:{ruleType}` with a 60s TTL gates the alert sink. The Grafana panel for suppressed alerts shows this working.

### Pluggable rules

`RuleEngine` is ~15 lines: it injects `List<Rule>`, streams over them, and flat-maps the `Optional<Alert>` results. Each rule is independently testable with no Spring context — just construct the rule, hand it a `RuleContext`, assert on the output.

### Dead letter queues + typed error handling

Both Kafka consumers use `ErrorHandlingDeserializer` to convert deserialization failures into recoverable exceptions, and a `DeadLetterPublishingRecoverer` that routes failed records to `trade-events-dlq` / `surveillance-alerts-dlq` with structured logs. Non-retryable exceptions (deserialization, NPE, IAE) skip the backoff and go straight to the DLQ.

### Custom Micrometer metrics

The surveillance-engine exports:
- `marketsentry_trades_processed_total` / `marketsentry_trades_duplicates_total`
- `marketsentry_trade_processing_seconds` (with p50/p95/p99 quantiles)
- `marketsentry_alerts_fired_total{rule, severity}`
- `marketsentry_alerts_suppressed_total{rule}`

These power the Surveillance Engine dashboard in Grafana (auto-provisioned).

### Cross-service JSON contracts

Each service has its own copy of `TradeEvent` / `Alert` in its own package. To make this work across the Kafka boundary, producers disable Jackson `__TypeId__` headers and consumers ignore them — JSON body is the contract, not the FQCN. (We learned this the hard way when the consumer tried to load `com.marketsentry.tradegenerator.model.TradeEvent` from a classpath that didn't have it.)

---

## Running locally

**Prerequisites:** Docker Desktop, Java 21, an IDE that can run Spring Boot apps.

### 1. Start the infrastructure

```bash
cd infrastructure
docker compose up -d
```

This brings up Kafka, Postgres, Redis, Prometheus, Grafana, and a Kafka UI. Ollama is **not** in compose — see step 2.

### 2. Install and start Ollama natively

On macOS, native Ollama uses the Apple Silicon GPU directly; the Docker version on Mac is CPU-only and much slower.

```bash
brew install ollama
brew services start ollama        # auto-starts at login from here on
ollama pull phi3:mini             # ~2 GB, used by ai-summarizer by default
```

### 3. Start the three services in your IDE

In order (the consumers should be ready before the producer starts):

1. `surveillance-engine/.../SurveillanceEngineApplication.java`
2. `ai-summarizer/.../AiSummarizerApplication.java`
3. `trade-generator/.../TradeGeneratorApplication.java`

### 4. Verify it works

```bash
# Fire each anomaly type once
curl -X POST http://localhost:8081/generator/inject/HIGH_FREQUENCY
curl -X POST http://localhost:8081/generator/inject/VOLUME_SPIKE
curl -X POST http://localhost:8081/generator/inject/REVERSAL

# Look at the latest alert
docker exec marketsentry-postgres psql -U marketsentry -d marketsentry \
  -c "SELECT alert_id, trader_id, rule_triggered FROM alerts ORDER BY timestamp DESC LIMIT 5;"

# Look at the AI summaries (gives the LLM ~10s to respond)
curl -s http://localhost:8083/summaries | python3 -m json.tool | head
```

### Dashboards and UIs

| URL | What |
|---|---|
| http://localhost:3000 | Grafana — `admin` / `admin` — MarketSentryAI → Surveillance Engine |
| http://localhost:9090 | Prometheus (raw queries, target health) |
| http://localhost:8090 | Kafka UI (topics, partitions, consumer groups, message peek) |
| http://localhost:8083/summaries | All AI summaries as JSON |

---

## Project layout

```
trade-generator/          # Spring Boot service — produces trades + anomaly injection
surveillance-engine/      # Spring Boot service — rules + state + alerts
ai-summarizer/            # Spring Boot service — Ollama wrapper + summary persistence
infrastructure/
  docker-compose.yml      # all backing services
  prometheus/             # scrape config
  grafana/
    datasources/          # Prometheus datasource provisioning
    dashboards/           # Surveillance Engine dashboard JSON + provisioning
sample-data/              # fixtures / test data
docs/                     # design notes
```

## Configuration knobs worth knowing

| File | Setting | What it does |
|---|---|---|
| `trade-generator/application.yml` | `marketsentry.generator.autostart` | `false` boots paused; control via `/generator/start` |
| `trade-generator/application.yml` | `marketsentry.generator.rate-ms` | milliseconds between generated trades |
| `trade-generator/application.yml` | `marketsentry.generator.anomaly-injection.enabled` | runs the scheduled injectors in the background |
| `surveillance-engine/application.yml` | `marketsentry.surveillance.alert-cooldown-seconds` | per-(trader, rule) cooldown TTL |
| `surveillance-engine/application.yml` | `marketsentry.surveillance.rules.*` | thresholds for each rule |
| `ai-summarizer/application.yml` | `marketsentry.ollama.model` | swap `phi3:mini` ↔ `mistral:7b` etc. |

---

## What's not in scope

- No authentication / RBAC — single-user dev setup.
- No horizontal scaling story — Kafka partitioning is in place (`traderId` is the key) but only one instance of each service runs at a time.
- LLM responses are best-effort. Failures route to the DLQ; there's no fallback summary.
- The web UI is a TODO — for now, alerts and summaries are inspected via SQL or the JSON endpoint.
