package com.marketsentry.surveillanceengine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraderState {

    private String traderId;
    private BigDecimal avgTradeVolume;
    private String lastTradeSide;
    private long lastTradeTimestampMs;
}
