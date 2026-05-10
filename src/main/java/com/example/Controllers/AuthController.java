package com.example.Controllers;

import com.example.Helpers.Roles;
import com.example.Helpers.SecurityUtils;
import com.example.Interfaces.IJwtService;
import com.example.Interfaces.IUserService;
import com.example.Implementation.JwtService;
import com.example.Implementation.UserService;
import com.example.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@WebServlet("/auth/*")
public class AuthController extends HttpServlet {

    private final IUserService userService = new UserService();
    private final IJwtService jwtService = new JwtService();
    private final ObjectMapper mapper = new ObjectMapper();
    private String redisHost;
    private int redisPort;
    private String usersCacheKey;
    private int usersCacheTtlSeconds;

    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        redisHost = requireContextParam(context, "redis.host");
        usersCacheKey = requireContextParam(context, "users.cache.key");
        redisPort = parseRequiredIntParam(context, "redis.port");
        usersCacheTtlSeconds = parseRequiredIntParam(context, "users.cache.ttl.seconds");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if ("/signup".equals(path)) {
            handleUserRegistration(req, resp, true);
        }
        else if ("/admin/add-user".equals(path)) {
            if (!SecurityUtils.isAdmin(req)) {
                sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "Access Denied: Admins only");
                return;
            }
            handleUserRegistration(req, resp, false);
        }
        else if ("/login".equals(path)) {
            handleLogin(req, resp);
        }
        else {
            sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found");
        }
    }
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if ("/users".equals(path)) {
            if (!SecurityUtils.isAdmin(req)) {
                sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "Access Denied: Admins only");
                return;
            }

            try (Jedis jedis = new Jedis(redisHost, redisPort)) {
                String cachedUsers = jedis.get(usersCacheKey);
                if (cachedUsers != null) {
                    System.out.println("[UserCache] HIT key=" + usersCacheKey);
                    resp.getWriter().write(cachedUsers);
                    return;
                }

                System.out.println("[UserCache] MISS key=" + usersCacheKey + " -> loading from DB");
                List<User> users = userService.getAllUsers();
                String usersJson = mapper.writeValueAsString(users);

                jedis.setex(usersCacheKey, usersCacheTtlSeconds, usersJson);
                System.out.println("[UserCache] SET key=" + usersCacheKey + " ttl=" + usersCacheTtlSeconds + "s");
                resp.getWriter().write(usersJson);
                return;
            } catch (Exception e) {
                System.err.println("[UserCache] Redis unavailable, fallback to DB. Reason: " + e.getMessage());
            }

            List<User> users = userService.getAllUsers();
            resp.getWriter().write(mapper.writeValueAsString(users));
        }
        else if ("/logout".equals(path)) {
            resp.getWriter().write("{\"message\": \"Logged out successfully\"}");
        }
    }


    private void handleUserRegistration(HttpServletRequest req, HttpServletResponse resp, boolean isPublicSignup) throws IOException {
        try {
            User user = mapper.readValue(req.getInputStream(), User.class);

            if (isStringEmpty(user.getUsername()) || user.getUsername().length() < 3) {
                sendErrorResponse(resp, 400, "Username must be at least 3 characters");
                return;
            }
            if (isStringEmpty(user.getPassword()) || user.getPassword().length() < 6) {
                sendErrorResponse(resp, 400, "Password must be at least 6 characters");
                return;
            }
            if (isStringEmpty(user.getEmail()) || !user.getEmail().contains("@")) {
                sendErrorResponse(resp, 400, "Invalid email format");
                return;
            }

            if (isPublicSignup) {
                user.setRole(Roles.USER);
            } else if (isStringEmpty(user.getRole())) {
                sendErrorResponse(resp, 400, "Role is required for admin-created users");
                return;
            }

            // 3. Database Operation
            if (userService.signUp(user)) {
                invalidateUsersCache();
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write(String.format("{\"message\": \"User created successfully as %s\"}", user.getRole()));
            } else {
                sendErrorResponse(resp, 400, "Registration failed. User may already exist.");
            }
        } catch (Exception e) {
            sendErrorResponse(resp, 400, "Invalid JSON format");
        }
    }
    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            User loginReq = mapper.readValue(req.getInputStream(), User.class);

            if (isStringEmpty(loginReq.getUsername()) || isStringEmpty(loginReq.getPassword())) {
                sendErrorResponse(resp, 400, "Username and password are required");
                return;
            }
            User user = userService.login(loginReq.getUsername(), loginReq.getPassword());

            if (user != null) {
                List<String> userRoles = Collections.singletonList(user.getRole());
                String token = jwtService.generateToken(user.getId(), userRoles);

                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write("{\"token\": \"" + token + "\"}");
            } else {
                sendErrorResponse(resp, 401, "Invalid username or password");
            }
        } catch (Exception e) {
            sendErrorResponse(resp, 400, "Invalid login request");
        }
    }
    private boolean isStringEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    private void sendErrorResponse(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(String.format("{\"error\": \"%s\", \"status\": %d}", message, status));
    }

    private void invalidateUsersCache() {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            jedis.del(usersCacheKey);
            System.out.println("[UserCache] INVALIDATE key=" + usersCacheKey);
        } catch (Exception e) {
            System.err.println("[UserCache] Failed to invalidate key=" + usersCacheKey + ". Reason: " + e.getMessage());
        }
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