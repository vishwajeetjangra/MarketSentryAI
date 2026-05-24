package com.marketsentry.surveillanceengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Data
@ConfigurationProperties(prefix = "marketsentry.surveillance.rules")
public class RulesProperties {

    private HighFrequency highFrequency = new HighFrequency();
    private VolumeSpike   volumeSpike   = new VolumeSpike();
    private RapidReversal rapidReversal = new RapidReversal();

    @Data
    public static class HighFrequency {
        private long thresholdTradesPerMinute = 30;
    }

    @Data
    public static class VolumeSpike {
        private BigDecimal multiplier    = new BigDecimal("5");
        private long       warmupTrades  = 10;
    }

    @Data
    public static class RapidReversal {
        private long thresholdReversals = 5;
    }
}
