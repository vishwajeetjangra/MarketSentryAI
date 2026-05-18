package com.marketsentry.surveillanceengine.model;

import jakarta.persistence.*;
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
@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @Column(name = "trade_id")
    private String tradeId;

    @Column(name = "trader_id", nullable = false)
    private String traderId;

    @Column(name = "stock", nullable = false)
    private String stock;

    @Column(name = "side", nullable = false)
    private String side;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "price", nullable = false, precision = 12, scale = 4)
    private BigDecimal price;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
