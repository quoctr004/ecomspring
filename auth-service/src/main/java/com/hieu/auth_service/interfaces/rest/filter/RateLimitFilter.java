package com.hieu.auth_service.interfaces.rest.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * C5: Per-IP rate limiter for /login and /register, backed by Redis.
 *
 * <h2>Design choices</h2>
 *
 * <p><b>Atomic Lua script</b> — the script increments the counter, sets TTL on
 * first hit, and returns both {@code [count, ttl_seconds]} in a single round
 * trip. This eliminates two problems the naive {@code INCR} + {@code EXPIRE}
 * pair has:
 * <ul>
 *   <li><b>Race condition</b>: if the process crashes between INCR and EXPIRE,
 *       the key lives forever (no TTL set).</li>
 *   <li><b>Two round trips</b> = 2× network latency per request.</li>
 * </ul>
 *
 * <p><b>Externalised config</b> via {@link RateLimitProperties} — limits, windows
 * and whitelisted IPs live in {@code application.yaml}. Ops can tune throttle
 * without rebuilding the image.
 *
 * <p><b>Fail-open</b> on Redis failure — brief Redis outage should not lock
 * everyone out of authentication. We log a warning and let the request proceed.
 *
 * <p><b>Accurate {@code Retry-After}</b> — the script returns the actual key TTL,
 * so the response tells the client exactly when to retry instead of always
 * advertising the full window.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String LOGIN_PATH    = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";

    /** Key prefix; full key is {@code rate_limit:{tag}:{ip}}. */
    private static final String KEY_PREFIX = "rate_limit:";

    /**
     * Lua: atomically INCR a key, set TTL on first hit, return {@code [count, ttl]}.
     *
     * <pre>
     * KEYS[1] = redis key
     * ARGV[1] = window in seconds
     *
     * returns: {count, remaining-ttl-seconds}
     * </pre>
     *
     * Executed atomically — no race window between INCR and EXPIRE.
     */
    private static final RedisScript<List> INCR_AND_EXPIRE = new DefaultRedisScript<>(
            """
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
                return {c, tonumber(ARGV[1])}
            end
            local ttl = redis.call('TTL', KEYS[1])
            return {c, ttl}
            """,
            List.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RateLimitProperties props;
    private final Set<String> whitelist;

    public RateLimitFilter(StringRedisTemplate redis,
                           ObjectMapper objectMapper,
                           RateLimitProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.props = props;
        this.whitelist = props.whitelistSet();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        RateLimitProperties.Endpoint config = configFor(request.getRequestURI());
        if (config == null) {
            // Not a protected endpoint — pass straight through.
            chain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        if (whitelist.contains(ip)) {
            chain.doFilter(request, response);
            return;
        }

        Result result = consume(keyFor(request.getRequestURI(), ip), config);
        if (result.exceeded()) {
            writeTooManyRequests(response, result.retryAfterSeconds());
            return;
        }

        chain.doFilter(request, response);
    }

    // ── lookups ───────────────────────────────────────────────────────────────

    private RateLimitProperties.Endpoint configFor(String path) {
        if (LOGIN_PATH.equals(path))    return props.login();
        if (REGISTER_PATH.equals(path)) return props.register();
        return null;
    }

    private static String keyFor(String path, String ip) {
        String tag = LOGIN_PATH.equals(path) ? "login" : "register";
        return KEY_PREFIX + tag + ":" + ip;
    }

    /**
     * Picks the first IP in {@code X-Forwarded-For} when present (this service
     * sits behind the api-gateway, which appends the real client IP). Falls
     * back to the socket remote address.
     *
     * <p>Trust XFF only when the gateway in front strips client-supplied
     * copies; otherwise a malicious client could spoof their IP to dodge
     * per-IP throttling.
     */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    // ── Redis ─────────────────────────────────────────────────────────────────

    /**
     * Runs the atomic Lua script. On any Redis failure we fail open — return
     * a non-exceeded result so the request proceeds rather than taking auth
     * down with Redis.
     */
    private Result consume(String key, RateLimitProperties.Endpoint cfg) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> reply = redis.execute(
                    INCR_AND_EXPIRE,
                    List.of(key),
                    String.valueOf(cfg.window().toSeconds()));

            if (reply == null || reply.size() < 2) {
                return Result.allowed(cfg.window());
            }
            long count = reply.get(0);
            long ttl   = reply.get(1);
            return new Result(count > cfg.limit(), Math.max(ttl, 1));

        } catch (Exception e) {
            log.warn("Redis unavailable for rate limit (key={}); failing open: {}",
                    key, e.getMessage());
            return Result.allowed(cfg.window());
        }
    }

    // ── response ──────────────────────────────────────────────────────────────

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(response.getWriter(),
                Map.of(
                        "code", "AUTH-1014",
                        "message", "Too many requests",
                        "retryAfterSeconds", retryAfterSeconds));
    }
    /** Result of the rate-limit check. */
    private record Result(boolean exceeded, long retryAfterSeconds) {
        static Result allowed(Duration window) {
            return new Result(false, window.toSeconds());
        }
    }
}
