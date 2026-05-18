-- MarketSentryAI — PostgreSQL schema init
-- Run automatically via JPA ddl-auto=update, but kept here for reference/manual setup

CREATE TABLE IF NOT EXISTS trades (
    trade_id    VARCHAR(50)    PRIMARY KEY,
    trader_id   VARCHAR(20)    NOT NULL,
    stock       VARCHAR(10)    NOT NULL,
    side        VARCHAR(4)     NOT NULL,
    quantity    BIGINT         NOT NULL,
    price       DECIMAL(12, 4) NOT NULL,
    timestamp   TIMESTAMP      NOT NULL
);

CREATE TABLE IF NOT EXISTS alerts (
    alert_id        VARCHAR(50)  PRIMARY KEY,
    trader_id       VARCHAR(20)  NOT NULL,
    rule_triggered  VARCHAR(60)  NOT NULL,
    severity        VARCHAR(10)  NOT NULL,
    message         TEXT,
    timestamp       TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_summaries (
    summary_id  VARCHAR(50) PRIMARY KEY,
    alert_id    VARCHAR(50) NOT NULL REFERENCES alerts(alert_id),
    ai_summary  TEXT,
    created_at  TIMESTAMP   NOT NULL
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_trades_trader_id    ON trades(trader_id);
CREATE INDEX IF NOT EXISTS idx_trades_timestamp    ON trades(timestamp);
CREATE INDEX IF NOT EXISTS idx_alerts_trader_id    ON alerts(trader_id);
CREATE INDEX IF NOT EXISTS idx_alerts_severity     ON alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alerts_timestamp    ON alerts(timestamp);
CREATE INDEX IF NOT EXISTS idx_summaries_alert_id  ON ai_summaries(alert_id);
