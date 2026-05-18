package com.marketsentry.aisummarizer.repository;

import com.marketsentry.aisummarizer.model.AiSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SummaryRepository extends JpaRepository<AiSummary, String> {

    Optional<AiSummary> findByAlertId(String alertId);
}
