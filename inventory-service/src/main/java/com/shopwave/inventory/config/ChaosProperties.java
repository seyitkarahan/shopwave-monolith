package com.shopwave.inventory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "shopwave.chaos")
public class ChaosProperties {
    private boolean enabled = false;
    private long delayMs = 0;
    /** 0.0–1.0: reserve sırasında 503 dönme olasılığı */
    private double failRate = 0;
}
