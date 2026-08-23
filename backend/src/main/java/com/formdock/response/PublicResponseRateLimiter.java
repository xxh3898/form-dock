package com.formdock.response;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PublicResponseRateLimiter {

    private static final String UNKNOWN_PEER = "unknown";

    private final int maxRequests;
    private final Duration window;
    private final int maxIdentities;
    private final Clock clock;
    private final LinkedHashMap<String, WindowState> states =
            new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public PublicResponseRateLimiter(PublicResponseRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    PublicResponseRateLimiter(PublicResponseRateLimitProperties properties, Clock clock) {
        if (properties.getMaxRequests() <= 0) {
            throw new IllegalArgumentException("Public Response rate limit must be positive");
        }
        if (properties.getWindow() == null
                || properties.getWindow().isZero()
                || properties.getWindow().isNegative()) {
            throw new IllegalArgumentException("Public Response rate window must be positive");
        }
        if (properties.getMaxIdentities() <= 0) {
            throw new IllegalArgumentException("Public Response rate identity limit must be positive");
        }
        this.maxRequests = properties.getMaxRequests();
        this.window = properties.getWindow();
        this.maxIdentities = properties.getMaxIdentities();
        this.clock = clock;
    }

    synchronized void check(String serverObservedPeer) {
        String identity = serverObservedPeer == null || serverObservedPeer.isBlank()
                ? UNKNOWN_PEER
                : serverObservedPeer;
        Instant now = clock.instant();
        WindowState current = states.get(identity);
        if (current != null && !isExpired(current, now)) {
            if (current.count() >= maxRequests) {
                throw PublicResponseException.rateLimited();
            }
            states.put(identity, new WindowState(current.startedAt(), current.count() + 1));
            return;
        }
        if (current != null) {
            states.remove(identity);
        }

        makeCapacity(now);
        states.put(identity, new WindowState(now, 1));
    }

    synchronized int trackedIdentityCount() {
        return states.size();
    }

    private void makeCapacity(Instant now) {
        if (states.size() < maxIdentities) {
            return;
        }
        Iterator<Map.Entry<String, WindowState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            if (isExpired(iterator.next().getValue(), now)) {
                iterator.remove();
            }
        }
        while (states.size() >= maxIdentities) {
            Iterator<String> eldest = states.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }

    private boolean isExpired(WindowState state, Instant now) {
        return !now.isBefore(state.startedAt().plus(window));
    }

    private record WindowState(Instant startedAt, int count) {
    }
}
