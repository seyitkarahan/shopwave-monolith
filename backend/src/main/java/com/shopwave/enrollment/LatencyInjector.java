package com.shopwave.enrollment;

import com.shopwave.exception.LatencyStopException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@ConfigurationProperties(prefix = "lab.latency")
public class LatencyInjector {
    private boolean enabled = true;
    private long delayMs = 0;
    private long jitterMs = 0;
    private boolean stopEnabled = true;
    private long stopThresholdMs = 3000;

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

        if (stopEnabled && total >= stopThresholdMs) {
            throw new LatencyStopException(
                    "Latency stop triggered: totalDelayMs=" + total + " thresholdMs=" + stopThresholdMs);
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

    public boolean isStopEnabled() {
        return stopEnabled;
    }

    public void setStopEnabled(boolean stopEnabled) {
        this.stopEnabled = stopEnabled;
    }

    public long getStopThresholdMs() {
        return stopThresholdMs;
    }

    public void setStopThresholdMs(long stopThresholdMs) {
        this.stopThresholdMs = stopThresholdMs;
    }
}
