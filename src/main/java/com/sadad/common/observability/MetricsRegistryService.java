package com.sadad.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricsRegistryService {

    private final MeterRegistry meterRegistry;

    public void incrementSettlement(String authority, boolean auto) {
        Counter.builder("sadad.wallet.settlement.count")
                .tag("authority", authority)
                .tag("mode", auto ? "AUTOMATED" : "MANUAL")
                .register(meterRegistry)
                .increment();
    }

    public void recordReload(double amount, String channel) {
        Counter.builder("sadad.wallet.reload.total")
                .tag("channel", channel)
                .register(meterRegistry)
                .increment(amount);
    }
}