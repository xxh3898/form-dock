package com.formdock.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class PublicResponseRateLimiterTest {

    @Test
    void should_rejectAfterConfiguredThresholdAndReset_when_windowExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-21T00:00:00Z"));
        PublicResponseRateLimiter limiter = new PublicResponseRateLimiter(
                properties(2, Duration.ofMinutes(1), 10),
                clock);

        assertThatCode(() -> limiter.check("198.51.100.10")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("198.51.100.10")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check("198.51.100.10"))
                .isInstanceOf(PublicResponseException.class)
                .extracting(failure -> ((PublicResponseException) failure).kind())
                .isEqualTo(PublicResponseException.Kind.RATE_LIMITED);

        clock.advance(Duration.ofMinutes(1));

        assertThatCode(() -> limiter.check("198.51.100.10")).doesNotThrowAnyException();
    }

    @Test
    void should_keepIdentityStateBounded_when_newPeersExceedCapacity() {
        PublicResponseRateLimiter limiter = new PublicResponseRateLimiter(
                properties(1, Duration.ofHours(1), 2),
                Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC));

        limiter.check("198.51.100.1");
        limiter.check("198.51.100.2");
        limiter.check("198.51.100.3");

        assertThat(limiter.trackedIdentityCount()).isEqualTo(2);
        assertThatCode(() -> limiter.check("198.51.100.1")).doesNotThrowAnyException();
        assertThat(limiter.trackedIdentityCount()).isEqualTo(2);
    }

    private PublicResponseRateLimitProperties properties(
            int maxRequests,
            Duration window,
            int maxIdentities) {
        PublicResponseRateLimitProperties properties = new PublicResponseRateLimitProperties();
        properties.setMaxRequests(maxRequests);
        properties.setWindow(window);
        properties.setMaxIdentities(maxIdentities);
        return properties;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
