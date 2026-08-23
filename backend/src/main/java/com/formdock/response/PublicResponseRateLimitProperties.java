package com.formdock.response;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("formdock.public-response.rate-limit")
public class PublicResponseRateLimitProperties {

    private int maxRequests = 60;
    private Duration window = Duration.ofMinutes(1);
    private int maxIdentities = 10_000;

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public int getMaxIdentities() {
        return maxIdentities;
    }

    public void setMaxIdentities(int maxIdentities) {
        this.maxIdentities = maxIdentities;
    }
}
