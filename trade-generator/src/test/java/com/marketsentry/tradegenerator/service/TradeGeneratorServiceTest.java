package com.marketsentry.tradegenerator.service;

import com.marketsentry.tradegenerator.model.TradeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeGeneratorServiceTest {

    private KafkaTemplate<String, TradeEvent> kafkaTemplate;
    private TaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledFuture;
    private TradeGeneratorService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        taskScheduler = mock(TaskScheduler.class);
        scheduledFuture = mock(ScheduledFuture.class);

        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
                .thenAnswer(inv -> scheduledFuture);

        service = new TradeGeneratorService(kafkaTemplate, taskScheduler);
        // @Value-injected fields
        ReflectionTestUtils.setField(service, "tradeEventsTopic", "trade-events");
        ReflectionTestUtils.setField(service, "initialRateMs", 500L);
        ReflectionTestUtils.setField(service, "autostart", false);
        ReflectionTestUtils.setField(service, "rateMs", 500L);
    }

    @Test
    void notRunningInitially() {
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void start_schedulesTaskAndReportsRunning() {
        when(scheduledFuture.isCancelled()).thenReturn(false);

        boolean changed = service.start();

        assertThat(changed).isTrue();
        assertThat(service.isRunning()).isTrue();
        verify(taskScheduler, times(1)).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMillis(500L)));
    }

    @Test
    void start_isNoOpWhenAlreadyRunning() {
        when(scheduledFuture.isCancelled()).thenReturn(false);

        service.start();
        boolean second = service.start();

        assertThat(second).isFalse();
        verify(taskScheduler, times(1)).scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
    }

    @Test
    void stop_cancelsScheduledTask() {
        when(scheduledFuture.isCancelled()).thenReturn(false);
        service.start();

        boolean stopped = service.stop();

        assertThat(stopped).isTrue();
        assertThat(service.isRunning()).isFalse();
        verify(scheduledFuture).cancel(false);
    }

    @Test
    void stop_isNoOpWhenAlreadyStopped() {
        boolean stopped = service.stop();
        assertThat(stopped).isFalse();
        verify(scheduledFuture, never()).cancel(false);
    }

    @Test
    void setRate_reschedulesWithNewPeriodWhenRunning() {
        when(scheduledFuture.isCancelled()).thenReturn(false);
        service.start();

        service.setRate(250L);

        // First schedule at 500ms, then cancelled, then re-scheduled at 250ms
        ArgumentCaptor<Duration> periodCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(taskScheduler, times(2)).scheduleAtFixedRate(any(Runnable.class), periodCaptor.capture());
        assertThat(periodCaptor.getAllValues())
                .containsExactly(Duration.ofMillis(500L), Duration.ofMillis(250L));
    }

    @Test
    void setRate_doesNotScheduleWhenStopped() {
        service.setRate(250L);

        verify(taskScheduler, never()).scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void setRate_rejectsNonPositiveValues() {
        assertThatThrownBy(() -> service.setRate(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate must be >= 1");

        assertThatThrownBy(() -> service.setRate(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateTrade_publishesToKafkaWithTraderIdAsKey() {
        service.generateTrade();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TradeEvent> tradeCaptor = ArgumentCaptor.forClass(TradeEvent.class);
        verify(kafkaTemplate).send(eq("trade-events"), keyCaptor.capture(), tradeCaptor.capture());

        // Same-trader ordering requires that the Kafka message key equals the trade's traderId.
        assertThat(keyCaptor.getValue()).isEqualTo(tradeCaptor.getValue().getTraderId());
    }
}
