package io.jenkins.plugins.gitlabbranchsource.helpers;

import com.google.common.util.concurrent.RateLimiter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class GitlabApiRateLimiters {

    private GitlabApiRateLimiters() {}

    private static final ConcurrentMap<String, RateLimiter> LIMITERS = new ConcurrentHashMap<>();

    public static RateLimiter getOrNull(String serverUrl, Double permitsPerSecond) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return null;
        }
        if (permitsPerSecond == null || permitsPerSecond <= 0.0) {
            return null; // OFF
        }

        // Reconfigure sans recréer si déjà présent (RateLimiter supporte setRate)
        return LIMITERS.compute(serverUrl, (k, existing) -> {
            if (existing == null) {
                return RateLimiter.create(permitsPerSecond);
            }
            existing.setRate(permitsPerSecond);
            return existing;
        });
    }

    static void clearForTest() {
        LIMITERS.clear();
    }
}
