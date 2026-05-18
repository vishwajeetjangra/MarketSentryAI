package com.marketsentry.surveillanceengine.consumer;

import com.marketsentry.surveillanceengine.model.TradeEvent;
import com.marketsentry.surveillanceengine.processor.TradeProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventConsumer {

    private final TradeProcessor tradeProcessor;

    @KafkaListener(
            topics = "${marketsentry.kafka.topics.trade-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(TradeEvent tradeEvent) {
        log.debug("Received trade: {} from trader: {}", tradeEvent.getTradeId(), tradeEvent.getTraderId());
        tradeProcessor.process(tradeEvent);
    }
}
