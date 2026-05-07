package com.example.Filters;

import redis.clients.jedis.Jedis;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter("/*")
public class RateLimitFilter implements Filter {

    private final int MAX_REQUESTS = 100;
    private final int WINDOW_SECONDS = 60;
    private final String REDIS_HOST = "localhost";
    private final int REDIS_PORT = 5002;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("RateLimitFilter Initialized using Port: " + REDIS_PORT);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String userIp = httpRequest.getRemoteAddr();
        String key = "rate:limit:" + userIp;

        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {

            long count = jedis.incr(key);

            if (count == 1) {
                jedis.expire(key, WINDOW_SECONDS);
                System.out.println("⏱️ Timer started for key: " + key + " (" + WINDOW_SECONDS + "s)");
            }

            System.out.println(String.format("[RateLimit] IP: %s | Current Count: %d", userIp, count));

            if (count > MAX_REQUESTS) {
                System.out.println("Rate limit exceeded for IP: " + userIp);
                httpResponse.setStatus(429);
                httpResponse.setContentType("text/plain;charset=UTF-8");
                httpResponse.getWriter().write("Too many requests. Please try again later.");
                return;
            }

        } catch (Exception e) {
            System.err.println("Redis Filter Error: " + e.getMessage());
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}