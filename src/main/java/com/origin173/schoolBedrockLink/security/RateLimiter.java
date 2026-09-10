package com.origin173.schoolBedrockLink.security;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Small in-memory fixed-window/sliding-window limiter for a single server. */
public final class RateLimiter {

    private final Duration window;
    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile int maxRequests;

    public RateLimiter(int maxRequests, Duration window) {
        this(maxRequests, window, System::nanoTime);
    }

    public RateLimiter(int maxRequests, Duration window, LongSupplier nanoTime) {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        this.maxRequests = maxRequests;
        this.window = Objects.requireNonNull(window, "window");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        long now = nanoTime.getAsLong();
        long cutoff = now - window.toNanos();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket());
        synchronized (bucket) {
            while (!bucket.timestamps.isEmpty() && bucket.timestamps.peekFirst() <= cutoff) {
                bucket.timestamps.removeFirst();
            }
            if (bucket.timestamps.size() >= maxRequests) {
                return false;
            }
            bucket.timestamps.addLast(now);
            return true;
        }
    }

    public void updateMaxRequests(int maxRequests) {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        this.maxRequests = maxRequests;
    }

    public void cleanup() {
        long cutoff = nanoTime.getAsLong() - window.toNanos();
        buckets.entrySet().removeIf(entry -> {
            Bucket bucket = entry.getValue();
            synchronized (bucket) {
                while (!bucket.timestamps.isEmpty() && bucket.timestamps.peekFirst() <= cutoff) {
                    bucket.timestamps.removeFirst();
                }
                return bucket.timestamps.isEmpty();
            }
        });
    }

    public int size() {
        return buckets.size();
    }

    private static final class Bucket {
        private final Deque<Long> timestamps = new ArrayDeque<>();
    }
}
