package com.example.Filters;
import com.example.Implementation.JwtService;
import io.jsonwebtoken.Claims;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebFilter("/*")
public class AuthFilter implements Filter {

    private final JwtService _jwtService = new JwtService();


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getPathInfo();


        if (path != null && (path.equals("/login") || path.equals("/signup") || path.equals("/logout"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            if (request.getCookies() != null) {
                for (javax.servlet.http.Cookie c : request.getCookies()) {
                    if ("AUTH_TOKEN".equals(c.getName())) {
                        token = c.getValue();
                        break;
                    }
                }
            }
        }

        if (token != null) {
            try {
                Claims claims = _jwtService.validateToken(token);

                int userId = Integer.parseInt(claims.getSubject());
                List<String> roles = claims.get("roles", List.class);

                request.setAttribute("userId", userId);
                request.setAttribute("userRoles", roles);

                filterChain.doFilter(request, response);
                return;

            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid or expired token.");
                return;
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("Access Denied: Missing Authorization Header");
    }


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
        
    }
}
