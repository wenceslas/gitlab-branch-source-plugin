package io.jenkins.plugins.gitlabbranchsource.helpers;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Shared quota limiters for GitLab API calls, keyed by server URL. The quota is enforced on a sliding 15-minute window.
 */
public final class GitLabApiQuotaLimiters {

    private GitLabApiQuotaLimiters() {}

    private static final int DEFAULT_REQUESTS_PER_15_MIN = 1900;

    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);

    private static final ConcurrentMap<String, SlidingWindowQuotaLimiter> LIMITERS = new ConcurrentHashMap<>();

    @CheckForNull
    public static SlidingWindowQuotaLimiter getOrNull(
            @CheckForNull String serverUrl, @CheckForNull Integer maxRequestsPer15Min) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return null;
        }

        /*
        if (maxRequestsPer15Min == null || maxRequestsPer15Min <= 0)
        {
            return null;
        }
        */

        int quota = (maxRequestsPer15Min == null || maxRequestsPer15Min <= 0)
                ? DEFAULT_REQUESTS_PER_15_MIN
                : maxRequestsPer15Min;

        return LIMITERS.compute(serverUrl, (key, existing) -> {
            if (existing == null) {
                return new SlidingWindowQuotaLimiter(quota, DEFAULT_WINDOW);
            }
            existing.reconfigure(maxRequestsPer15Min, DEFAULT_WINDOW);
            return existing;
        });
    }

    static void clearForTest() {
        LIMITERS.clear();
    }

    public static final class SlidingWindowQuotaLimiter {

        private int maxRequests;

        private long windowMillis;

        private final Deque<Long> timestamps = new ArrayDeque<>();

        SlidingWindowQuotaLimiter(int maxRequests, Duration window) {
            this.maxRequests = maxRequests;
            this.windowMillis = window.toMillis();
        }

        public synchronized void reconfigure(int maxRequests, Duration window) {
            this.maxRequests = maxRequests;
            this.windowMillis = window.toMillis();
            evictOld(System.currentTimeMillis());

            while (timestamps.size() > maxRequests) {
                timestamps.pollFirst();
            }
        }

        /**
         * Blocks until the caller is allowed to consume one request from the quota.
         */
        public void acquire() throws InterruptedException {
            while (true) {
                long sleepMillis;

                synchronized (this) {
                    long now = System.currentTimeMillis();
                    evictOld(now);

                    if (timestamps.size() < maxRequests) {
                        timestamps.addLast(now);
                        return;
                    }

                    long oldest = timestamps.peekFirst();
                    sleepMillis = windowMillis - (now - oldest);

                    if (sleepMillis <= 0L) {
                        timestamps.pollFirst();
                        continue;
                    }
                }

                Thread.sleep(sleepMillis);
            }
        }

        private void evictOld(long now) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMillis) {
                timestamps.pollFirst();
            }
        }
    }
}
