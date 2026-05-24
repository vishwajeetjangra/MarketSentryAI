package com.marketsentry.surveillanceengine;

import com.marketsentry.surveillanceengine.config.RulesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RulesProperties.class)
public class SurveillanceEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(SurveillanceEngineApplication.class, args);
    }
}
