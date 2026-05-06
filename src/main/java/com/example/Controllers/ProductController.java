package com.example.Controllers;

import com.example.Helpers.SecurityUtils;
import com.example.Implementation.ProductSqlService;
import com.example.Interfaces.IProductSqlService;
import com.example.model.Product;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.List;

@WebServlet("/api/products/*")
public class ProductController extends HttpServlet {

    private final IProductSqlService productService = new ProductSqlService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String pathInfo = req.getPathInfo();

        if (pathInfo == null || pathInfo.equals("/")) {
            List<Product> products = productService.getAllProducts();
            mapper.writeValue(resp.getWriter(), products);
            return;
        }

        try {
            int id = getIdFromPath(req);

            Product product = productService.getProductById(id);

            if (product == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Product not found");
                return;
            }

            mapper.writeValue(resp.getWriter(), product);

        } catch (NumberFormatException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid product id");
        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch product: " + e.getMessage());
        }
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
            Product newProduct = mapper.readValue(req.getReader(), Product.class);

            if (newProduct == null ||
                    isNullOrEmpty(newProduct.getName()) ||
                    newProduct.getPrice() <= 0 ||
                    newProduct.getStock() < 0 ||
                    newProduct.getCategoryId() <= 0) {

                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid product data: Name, Price, Stock and Category are required");
                return;
            }

            Product createdProduct = productService.createProduct(newProduct);

            if (createdProduct == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Category does not exist or product creation failed");
                return;
            }

            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write("{\"message\": \"Product added successfully\", \"productId\": " + createdProduct.getId() + "}");

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

            Product product = mapper.readValue(req.getReader(), Product.class);

            if (product == null ||
                    isNullOrEmpty(product.getName()) ||
                    product.getPrice() <= 0 ||
                    product.getStock() < 0 ||
                    product.getCategoryId() <= 0) {

                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid product data: Name, Price, Stock and Category are required");
                return;
            }

            Product updatedProduct = productService.updateProduct(id, product);

            if (updatedProduct == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Product not found or category does not exist");
                return;
            }

            resp.getWriter().write("{\"message\": \"Product updated successfully\"}");

        } catch (NumberFormatException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid product id");
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

            boolean deleted = productService.deleteProduct(id);

            if (!deleted) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Product not found");
                return;
            }

            resp.getWriter().write("{\"message\": \"Product deleted successfully\"}");

        } catch (NumberFormatException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid product id");
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
}