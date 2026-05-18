package com.marketsentry.surveillanceengine.repository;

import com.marketsentry.surveillanceengine.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, String> {

    List<Alert> findByTraderId(String traderId);

    List<Alert> findBySeverity(Alert.Severity severity);
}
