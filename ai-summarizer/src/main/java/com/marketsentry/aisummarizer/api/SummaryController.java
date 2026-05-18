package com.marketsentry.aisummarizer.api;

import com.marketsentry.aisummarizer.model.AiSummary;
import com.marketsentry.aisummarizer.repository.SummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/summaries")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryRepository summaryRepository;

    @GetMapping
    public List<AiSummary> getAllSummaries() {
        return summaryRepository.findAll();
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<AiSummary> getSummaryByAlert(@PathVariable String alertId) {
        return summaryRepository.findByAlertId(alertId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
