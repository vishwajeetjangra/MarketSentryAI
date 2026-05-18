package com.marketsentry.tradegenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeEvent {

    private String tradeId;
    private String traderId;
    private String stock;
    private TradeSide side;
    private Long quantity;
    private BigDecimal price;
    private LocalDateTime timestamp;

    public enum TradeSide {
        BUY, SELL
    }
}
