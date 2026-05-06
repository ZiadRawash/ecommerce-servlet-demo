package com.example.Implementation;

import com.example.model.Product;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/products")
public class ProductServlet extends HttpServlet {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        List<Product> products = new ArrayList<>();
        String query = "SELECT id, name, price, stock, category_id FROM Products";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                products.add(new Product(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getInt("stock"),
                        rs.getInt("category_id")
                ));
            }
            mapper.writeValue(resp.getWriter(), products);

        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch products: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if (req.getContentType() == null || !req.getContentType().contains("application/json")) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json");
            return;
        }

        try {
            Product newProduct = mapper.readValue(req.getReader(), Product.class);

            if (newProduct == null || isNullOrEmpty(newProduct.getName()) || newProduct.getPrice() <= 0) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid product data: Name and Price are required");
                return;
            }

            String sql = "INSERT INTO Products (name, price, stock, category_id) VALUES (?, ?, ?, ?)";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, newProduct.getName());
                ps.setDouble(2, newProduct.getPrice());
                ps.setInt(3, newProduct.getStock());
                if (newProduct.getCategoryId() > 0) {
                    ps.setInt(4, newProduct.getCategoryId());
                } else {
                    ps.setNull(4, Types.INTEGER);
                }

                ps.executeUpdate();

                ResultSet generatedKeys = ps.getGeneratedKeys();
                int newId = generatedKeys.next() ? generatedKeys.getInt(1) : 0;

                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write("{\"message\": \"Product added successfully\", \"productId\": " + newId + "}");
            }

        } catch (JsonProcessingException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON or invalid data types provided");
        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
        }
    }

    private void sendErrorResponse(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().write("{\"error\": \"" + message + "\"}");
    }

    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}