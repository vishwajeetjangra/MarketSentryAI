package com.marketsentry.tradegenerator.service;

import com.marketsentry.tradegenerator.model.TradeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AnomalyInjectorServiceTest {

    private static final String TOPIC = "trade-events";

    private KafkaTemplate<String, TradeEvent> kafkaTemplate;
    private AnomalyInjectorService injector;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        injector = new AnomalyInjectorService(kafkaTemplate);
        // @Value field is set by Spring in production; populate it directly here.
        ReflectionTestUtils.setField(injector, "tradeEventsTopic", TOPIC);
    }

    // ---------- injectHighFrequencyBurst ----------

    @Test
    void highFrequencyBurst_publishesThirtyFiveSameSideTrades() {
        injector.injectHighFrequencyBurst("T1001");

        List<TradeEvent> sent = captureSentTrades();
        assertThat(sent).hasSize(35);
        // All trades are BUY: an alternating-side burst would (incorrectly) also trigger
        // the reversal rule, so the injector keeps the side stable to test high-frequency cleanly.
        assertThat(sent).allMatch(t -> t.getSide() == TradeEvent.TradeSide.BUY);
        assertThat(sent).allMatch(t -> t.getTraderId().equals("T1001"));
    }

    @Test
    void highFrequencyBurst_returnsTraderUsed() {
        String trader = injector.injectHighFrequencyBurst("T1001");
        assertThat(trader).isEqualTo("T1001");
    }

    @Test
    void highFrequencyBurst_picksRandomAnomalyTraderWhenBlank() {
        String trader = injector.injectHighFrequencyBurst(null);
        assertThat(trader).isIn("T9001", "T9002", "T9003");

        String trader2 = injector.injectHighFrequencyBurst("");
        assertThat(trader2).isIn("T9001", "T9002", "T9003");
    }

    // ---------- injectVolumeSpike ----------

    @Test
    void volumeSpike_publishesOneLargeBuy() {
        injector.injectVolumeSpike("T9002");

        List<TradeEvent> sent = captureSentTrades();
        assertThat(sent).hasSize(1);
        TradeEvent trade = sent.get(0);
        assertThat(trade.getQuantity()).isEqualTo(50_000L);
        assertThat(trade.getSide()).isEqualTo(TradeEvent.TradeSide.BUY);
        assertThat(trade.getTraderId()).isEqualTo("T9002");
    }

    // ---------- injectRapidReversals ----------

    @Test
    void rapidReversals_publishesEightAlternatingTrades() {
        injector.injectRapidReversals("T9003");

        List<TradeEvent> sent = captureSentTrades();
        assertThat(sent).hasSize(8);
        // First trade is BUY, then alternates — guaranteeing >= 5 reversals in the consumer's window.
        for (int i = 0; i < sent.size(); i++) {
            TradeEvent.TradeSide expected = (i % 2 == 0) ? TradeEvent.TradeSide.BUY : TradeEvent.TradeSide.SELL;
            assertThat(sent.get(i).getSide()).as("trade %d", i).isEqualTo(expected);
        }
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private List<TradeEvent> captureSentTrades() {
        ArgumentCaptor<TradeEvent> captor = ArgumentCaptor.forClass(TradeEvent.class);
        verify(kafkaTemplate, org.mockito.Mockito.atLeastOnce())
                .send(eq(TOPIC), any(String.class), captor.capture());
        return captor.getAllValues();
    }
}
