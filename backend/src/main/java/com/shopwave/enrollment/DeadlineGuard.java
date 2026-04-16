package com.shopwave.enrollment;

import com.shopwave.exception.DeadlineExceededException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lab.deadline")
public class DeadlineGuard {
    private boolean enabled = false;
    private long timeoutMs = 1000;

    public long start() {
        return System.nanoTime();
    }

    public void check(long startedAtNanos, String operationName) {
        if (!enabled) {
            return;
        }

        long elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
        if (elapsedMs > timeoutMs) {
            throw new DeadlineExceededException(
                    operationName + " exceeded deadline: elapsed=" + elapsedMs + "ms timeout=" + timeoutMs + "ms");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
