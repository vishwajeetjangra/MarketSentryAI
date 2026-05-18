package com.marketsentry.surveillanceengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @Column(name = "alert_id")
    private String alertId;

    @Column(name = "trader_id", nullable = false)
    private String traderId;

    @Column(name = "rule_triggered", nullable = false)
    private String ruleTriggered;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
