package com.example.Controllers;

import com.example.Helpers.SecurityUtils;
import com.example.Implementation.CategorySqlService;
import com.example.Interfaces.ICategorySqlService;
import com.example.model.Category;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.List;

@WebServlet("/api/categories/*")
public class CategoryController extends HttpServlet {

    private final ICategorySqlService categoryService = new CategorySqlService();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 5002;
    private static final String CATEGORIES_CACHE_KEY = "categories:all";
    private static final int CATEGORIES_CACHE_TTL_SECONDS = 60;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            String cachedCategories = jedis.get(CATEGORIES_CACHE_KEY);
            if (cachedCategories != null) {
                System.out.println("[CategoryCache] HIT key=" + CATEGORIES_CACHE_KEY);
                resp.getWriter().write(cachedCategories);
                return;
            }

            System.out.println("[CategoryCache] MISS key=" + CATEGORIES_CACHE_KEY + " -> loading from DB");

            List<Category> categories = categoryService.getAllCategories();
            String categoriesJson = mapper.writeValueAsString(categories);

            jedis.setex(CATEGORIES_CACHE_KEY, CATEGORIES_CACHE_TTL_SECONDS, categoriesJson);
            System.out.println("[CategoryCache] SET key=" + CATEGORIES_CACHE_KEY + " ttl=" + CATEGORIES_CACHE_TTL_SECONDS + "s");
            resp.getWriter().write(categoriesJson);
            return;
        } catch (Exception e) {
            // Fallback to DB response when Redis is unavailable.
            System.err.println("[CategoryCache] Redis unavailable, fallback to DB. Reason: " + e.getMessage());
        }

        List<Category> categories = categoryService.getAllCategories();
        mapper.writeValue(resp.getWriter(), categories);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if (!SecurityUtils.isAdmin(req)) {
            sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "Access Denied: Admins only");
            return;
        }

        if (req.getContentType() == null || !req.getContentType().contains("application/json")) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json");
            return;
        }

        try {
            Category category = mapper.readValue(req.getReader(), Category.class);

            if (category == null || isNullOrEmpty(category.getName())) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid category data: Name is required");
                return;
            }

            Category createdCategory = categoryService.createCategory(category);

            if (createdCategory == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create category");
                return;
            }

            invalidateCategoriesCache();

            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write("{\"message\": \"Category added successfully\", \"categoryId\": " + createdCategory.getId() + "}");

        } catch (JsonProcessingException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON or invalid data types provided");
        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if (!SecurityUtils.isAdmin(req)) {
            sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "Access Denied: Admins only");
            return;
        }

        try {
            int id = getIdFromPath(req);

            Category category = mapper.readValue(req.getReader(), Category.class);

            if (category == null || isNullOrEmpty(category.getName())) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid category data: Name is required");
                return;
            }

            Category updatedCategory = categoryService.updateCategory(id, category);

            if (updatedCategory == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Category not found");
                return;
            }

            invalidateCategoriesCache();

            resp.getWriter().write("{\"message\": \"Category updated successfully\"}");

        } catch (NumberFormatException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid category id");
        } catch (JsonProcessingException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON or invalid data types provided");
        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if (!SecurityUtils.isAdmin(req)) {
            sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "Access Denied: Admins only");
            return;
        }

        try {
            int id = getIdFromPath(req);

            boolean deleted = categoryService.deleteCategory(id);

            if (!deleted) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Category not found");
                return;
            }

            invalidateCategoriesCache();

            resp.getWriter().write("{\"message\": \"Category deleted successfully\"}");

        } catch (NumberFormatException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid category id");
        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
        }
    }

    private int getIdFromPath(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();

        if (pathInfo == null || pathInfo.equals("/")) {
            throw new NumberFormatException("Missing id");
        }

        return Integer.parseInt(pathInfo.substring(1));
    }

    private void sendErrorResponse(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().write("{\"error\": \"" + message + "\"}");
    }

    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private void invalidateCategoriesCache() {
        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            jedis.del(CATEGORIES_CACHE_KEY);
            System.out.println("[CategoryCache] INVALIDATE key=" + CATEGORIES_CACHE_KEY);
        } catch (Exception ignored) {
            System.err.println("[CategoryCache] Failed to invalidate key=" + CATEGORIES_CACHE_KEY);
        }
    }
}