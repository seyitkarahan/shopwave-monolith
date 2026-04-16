package com.shopwave.enrollment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@ConfigurationProperties(prefix = "lab.latency")
public class LatencyInjector {
    private boolean enabled = true;
    private long delayMs = 0;
    private long jitterMs = 0;

    public void maybeSleep() {
        if (!enabled) {
            return;
        }

        long extra = (jitterMs > 0)
                ? ThreadLocalRandom.current().nextLong(0, jitterMs + 1)
                : 0;
        long total = delayMs + extra;
        if (total <= 0) {
            return;
        }

        try {
            Thread.sleep(total);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public long getJitterMs() {
        return jitterMs;
    }

    public void setJitterMs(long jitterMs) {
        this.jitterMs = jitterMs;
    }
}
