# MarketSentryAI — API Reference

Base URL: `http://localhost:8082`

---

## Alerts

### GET /alerts
Returns all alerts.

**Response:**
```json
[
  {
    "alertId": "ALT5001",
    "traderId": "T1001",
    "ruleTriggered": "HIGH_FREQUENCY_SPIKE",
    "severity": "HIGH",
    "message": "Trader executed 45 trades in 60 seconds",
    "timestamp": "2026-05-17T10:16:02"
  }
]
```

---

### GET /alerts/high-risk
Returns only HIGH and CRITICAL severity alerts.

---

### GET /alerts/{id}
Returns a single alert by ID.

---

## Traders

### GET /traders/{id}/risk
Returns all alerts for a specific trader.

---

### GET /traders/{id}/activity
Returns all trades executed by a specific trader.

---

### GET /traders/{id}/state
Returns the current Redis state for a trader (real-time activity window).
`tradesLast60s` is the live count from the Redis ZSET sliding window.

**Response:**
```json
{
  "traderId": "T1001",
  "tradesLast60s": 42,
  "avgTradeVolume": 200.00,
  "rapidReversalCount": 6,
  "lastTradeSide": "BUY",
  "lastTradeTimestampMs": 1716040562000
}
```

---

## AI Summaries

Base URL: `http://localhost:8083`

### GET /summaries
Returns all AI-generated summaries.

---

### GET /summaries/{alertId}
Returns the AI summary for a specific alert. `404` if no summary exists yet.

**Response:**
```json
{
  "summaryId": "a1b2c3d4-...",
  "alertId": "ALT3F9A1B2C",
  "aiSummary": "Potential spoofing-like behaviour detected due to unusually high trade frequency and abnormal volume.",
  "createdAt": "2026-05-18T10:16:05"
}
```
