package com.wireforge.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.wireforge.entity.AuthSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSessionCacheConfigTest {

    private final AtomicLong nanos = new AtomicLong();
    private Clock clock;
    private Cache<String, AuthSession> cache;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenAnswer(inv -> Instant.parse("2026-11-12T00:00:00Z").plusNanos(nanos.get()));
        cache = testCache(10, Scheduler.disabledScheduler());
    }

    private Cache<String, AuthSession> testCache(long maximumSize, Scheduler scheduler) {
        return AuthSessionCacheConfig.cacheBuilder(maximumSize, clock, scheduler)
                .ticker(nanos::get)
                .executor(Runnable::run)
                .build();
    }

    private AuthSession session(String token, Duration remaining) {
        LocalDateTime now = LocalDateTime.now(clock);
        return AuthSession.builder().token(token).userId("user-" + token)
                .createdAt(now.minusDays(6)).expiresAt(now.plus(remaining)).build();
    }

    @Test
    void springUsesDefaultCapacity() {
        new ApplicationContextRunner().withUserConfiguration(AuthSessionCacheConfig.class).run(context -> {
            assertNull(context.getStartupFailure());
            Cache<?, ?> configured = context.getBean("authSessionCache", Cache.class);
            assertEquals(10_000, configured.policy().eviction().orElseThrow().getMaximum());
        });
    }

    @Test
    void springBindsConfiguredCapacity() {
        new ApplicationContextRunner().withUserConfiguration(AuthSessionCacheConfig.class)
                .withPropertyValues("wireforge.auth.session-cache-max-size=23").run(context -> {
                    assertNull(context.getStartupFailure());
                    Cache<?, ?> configured = context.getBean("authSessionCache", Cache.class);
                    assertEquals(23, configured.policy().eviction().orElseThrow().getMaximum());
                });
    }

    @Test
    void rejectsNegativeCapacity() {
        assertThrows(IllegalArgumentException.class, () -> testCache(-1, Scheduler.disabledScheduler()));
    }

    @Test
    void oldSessionExpiresAtItsOriginalDeadline() {
        AuthSession old = session("old", Duration.ofMinutes(5));
        cache.put(old.getToken(), old);
        nanos.addAndGet(Duration.ofMinutes(5).minusNanos(1).toNanos());
        assertSame(old, cache.getIfPresent("old"));
        nanos.incrementAndGet();
        assertNull(cache.getIfPresent("old"));
    }

    @Test
    void readsAndReplacementDoNotExtendTheSession() {
        AuthSession original = session("active", Duration.ofHours(1));
        cache.put("active", original);
        nanos.addAndGet(Duration.ofMinutes(20).toNanos());
        assertSame(original, cache.getIfPresent("active"));
        nanos.addAndGet(Duration.ofMinutes(20).toNanos());
        AuthSession reloaded = session("active", Duration.ofMinutes(20));
        assertEquals(original.getExpiresAt(), reloaded.getExpiresAt());
        cache.put("active", reloaded);
        nanos.addAndGet(Duration.ofMinutes(20).toNanos());
        assertNull(cache.getIfPresent("active"));
    }

    @Test
    void expiredAndMissingDeadlinesAreNotRetained() {
        cache.put("expired", session("expired", Duration.ofSeconds(-1)));
        cache.put("now", session("now", Duration.ZERO));
        cache.put("missing", AuthSession.builder().token("missing").build());
        assertNull(cache.getIfPresent("expired"));
        assertNull(cache.getIfPresent("now"));
        assertNull(cache.getIfPresent("missing"));
        // 定时轮按时间片回收；推进虚拟时钟，不假设同一纳秒内完成物理移除。
        nanos.addAndGet(Duration.ofSeconds(2).toNanos());
        cache.cleanUp();
        assertEquals(0, cache.estimatedSize());
    }

    @Test
    void capacityEvictsExcessEntries() {
        Cache<String, AuthSession> small = testCache(2, Scheduler.disabledScheduler());
        for (int i = 0; i < 20; i++) {
            small.put("token-" + i, session("token-" + i, Duration.ofDays(7)));
        }
        small.cleanUp();
        assertEquals(2, small.estimatedSize());
    }

    @Test
    void zeroCapacityDisablesRetention() {
        Cache<String, AuthSession> disabled = testCache(0, Scheduler.disabledScheduler());
        disabled.put("token", session("token", Duration.ofDays(7)));
        disabled.cleanUp();
        assertEquals(0, disabled.estimatedSize());
    }

    @Test
    void scheduledMaintenanceRemovesIdleSessionsWithoutReadsOrCleanup() {
        List<Runnable> scheduled = new ArrayList<>();
        Scheduler scheduler = (executor, command, delay, unit) -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            scheduled.add(() -> {
                if (!future.isCancelled()) {
                    executor.execute(command);
                    future.complete(null);
                }
            });
            return future;
        };
        Cache<String, AuthSession> idle = testCache(10, scheduler);
        idle.put("idle", session("idle", Duration.ofSeconds(1)));
        assertEquals(1, idle.estimatedSize());
        assertFalse(scheduled.isEmpty(), "写入必须安排无请求时的过期维护");

        // 仅推进时钟并触发已安排的后台任务，不读缓存、不显式 cleanUp。
        nanos.addAndGet(Duration.ofSeconds(10).toNanos());
        List.copyOf(scheduled).forEach(Runnable::run);
        assertEquals(0, idle.estimatedSize());
    }

    @Test
    void anomalousFarFutureDateDoesNotOverflow() {
        AuthSession future = session("future", Duration.ofDays(7));
        future.setExpiresAt(LocalDateTime.MAX);
        assertDoesNotThrow(() -> cache.put("future", future));
        assertSame(future, cache.getIfPresent("future"));
    }
}
