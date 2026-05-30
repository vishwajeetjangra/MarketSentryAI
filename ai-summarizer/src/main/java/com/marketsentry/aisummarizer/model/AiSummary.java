package com.marketsentry.aisummarizer.model;

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
@Table(name = "ai_summaries")
public class AiSummary {

    @Id
    @Column(name = "summary_id")
    private String summaryId;

    @Column(name = "alert_id", nullable = false, unique = true)
    private String alertId;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
