package com.marketsentry.surveillanceengine.repository;

import com.marketsentry.surveillanceengine.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, String> {

    List<Trade> findByTraderId(String traderId);
}
