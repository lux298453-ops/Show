package com.wireforge.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.wireforge.entity.AuthSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/** 有界的会话查询缓存；淘汰不撤销数据库中的登录凭证。 */
@Configuration(proxyBeanMethods = false)
public class AuthSessionCacheConfig {

    @Bean
    public Cache<String, AuthSession> authSessionCache(
            @Value("${wireforge.auth.session-cache-max-size:10000}") long maximumSize) {
        return cacheBuilder(maximumSize, Clock.systemDefaultZone(), Scheduler.systemScheduler()).build();
    }

    static Caffeine<String, AuthSession> cacheBuilder(long maximumSize, Clock clock, Scheduler scheduler) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                // 无请求时也安排过期维护，不为每条会话创建独立线程或定时器。
                .scheduler(scheduler)
                .expireAfter(new Expiry<String, AuthSession>() {
                    @Override
                    public long expireAfterCreate(String token, AuthSession session, long currentTime) {
                        return remainingNanos(session, clock);
                    }

                    @Override
                    public long expireAfterUpdate(String token, AuthSession session,
                                                  long currentTime, long currentDuration) {
                        return remainingNanos(session, clock);
                    }

                    @Override
                    public long expireAfterRead(String token, AuthSession session,
                                                long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                });
    }

    private static long remainingNanos(AuthSession session, Clock clock) {
        if (session.getExpiresAt() == null) return 0;
        Duration remaining = Duration.between(LocalDateTime.now(clock), session.getExpiresAt());
        if (remaining.isNegative() || remaining.isZero()) return 0;
        try {
            return remaining.toNanos();
        } catch (ArithmeticException e) {
            // 历史异常日期不能使认证请求因纳秒转换溢出而失败。
            return Long.MAX_VALUE;
        }
    }
}
