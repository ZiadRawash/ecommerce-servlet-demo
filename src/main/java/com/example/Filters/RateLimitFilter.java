package com.example.Filters;

import redis.clients.jedis.Jedis;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter("/*")
public class RateLimitFilter implements Filter {

    private int maxRequests;
    private int windowSeconds;
    private String redisHost;
    private int redisPort;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext context = filterConfig.getServletContext();
        redisHost = requireContextParam(context, "redis.host");
        redisPort = parseRequiredIntParam(context, "redis.port");
        maxRequests = parseRequiredIntParam(context, "ratelimit.max.requests");
        windowSeconds = parseRequiredIntParam(context, "ratelimit.window.seconds");
        System.out.println("RateLimitFilter initialized with maxRequests=" + maxRequests + ", windowSeconds=" + windowSeconds + ", redisPort=" + redisPort);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String userIp = httpRequest.getRemoteAddr();
        String key = "rate:limit:" + userIp;

        try (Jedis jedis = new Jedis(redisHost, redisPort)) {

            long count = jedis.incr(key);

            if (count == 1) {
                jedis.expire(key, windowSeconds);
                System.out.println("[RateLimit] Timer started for key=" + key + " (" + windowSeconds + "s)");
            }

            System.out.println(String.format("[RateLimit] IP: %s | Current Count: %d", userIp, count));

            if (count > maxRequests) {
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

    private String requireContextParam(ServletContext context, String paramName) throws ServletException {
        String value = context.getInitParameter(paramName);
        if (value == null || value.trim().isEmpty()) {
            throw new ServletException("Missing required context-param: " + paramName);
        }
        return value.trim();
    }

    private int parseRequiredIntParam(ServletContext context, String paramName) throws ServletException {
        String value = requireContextParam(context, paramName);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ServletException("Invalid integer value for context-param " + paramName + ": " + value, e);
        }
    }
}