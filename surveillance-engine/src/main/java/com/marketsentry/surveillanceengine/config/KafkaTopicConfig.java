package com.marketsentry.surveillanceengine.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic tradeEventsTopic() {
        return TopicBuilder.name("trade-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic surveillanceAlertsTopic() {
        return TopicBuilder.name("surveillance-alerts")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic tradeEventsDlqTopic() {
        return TopicBuilder.name("trade-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
