package io.github.pratikpanchal22.authserver.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Per-IP rate limiting for the login form and OAuth2 token endpoint.
 * Registered via FilterRegistrationBean in RateLimitConfig — not a @Component.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    // 10 attempts per 10-minute window per IP
    private static final int LOGIN_CAPACITY       = 10;
    private static final Duration LOGIN_WINDOW    = Duration.ofMinutes(10);

    // 20 requests per minute per IP
    private static final int TOKEN_CAPACITY       = 20;
    private static final Duration TOKEN_WINDOW    = Duration.ofMinutes(1);

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> tokenBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();
        String ip   = extractIp(request);

        if ("/login".equals(path)) {
            Bucket bucket = loginBuckets.computeIfAbsent(ip, k -> newLoginBucket());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                long retryAfter = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.sendRedirect(request.getContextPath() + "/login?error=rate_limited");
                return;
            }
        } else if ("/oauth2/token".equals(path)) {
            Bucket bucket = tokenBuckets.computeIfAbsent(ip, k -> newTokenBucket());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                long retryAfter = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"error\":\"rate_limit_exceeded\"," +
                        "\"error_description\":\"Too many requests. Retry after " + retryAfter + " seconds.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private static Bucket newLoginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(LOGIN_CAPACITY)
                        .refillIntervally(LOGIN_CAPACITY, LOGIN_WINDOW)
                        .build())
                .build();
    }

    private static Bucket newTokenBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(TOKEN_CAPACITY)
                        .refillIntervally(TOKEN_CAPACITY, TOKEN_WINDOW)
                        .build())
                .build();
    }

    private static String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
