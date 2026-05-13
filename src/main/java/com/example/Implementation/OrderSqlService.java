package com.example.Implementation;

import com.example.Interfaces.IOrderService;
import com.example.model.Order;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderSqlService implements IOrderService {

    @Override
    public Order placeOrder(int userId) {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // Get Cart ID
            int cartId = -1;
            String cartSql = "SELECT id FROM Cart WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(cartSql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) cartId = rs.getInt("id");
                else throw new RuntimeException("No cart found for this user.");
            }

            // Check the stocking
            String itemsSql = "SELECT ci.product_id, ci.quantity, p.price, p.stock, p.name " +
                    "FROM CartItems ci JOIN Products p ON ci.product_id = p.id " +
                    "WHERE ci.cart_id = ?";

            double totalAmount = 0;
            List<OrderItemTemp> itemsToOrder = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(itemsSql)) {
                ps.setInt(1, cartId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int quantity = rs.getInt("quantity");
                    int stock = rs.getInt("stock");
                    String productName = rs.getString("name");

                    if (quantity > stock) {
                        conn.rollback();
                        throw new RuntimeException("Insufficient stock for product: " + productName);
                    }

                    totalAmount += (rs.getDouble("price") * quantity);
                    itemsToOrder.add(new OrderItemTemp(rs.getInt("product_id"), quantity, rs.getDouble("price")));
                }
            }

            if (itemsToOrder.isEmpty()) throw new RuntimeException("Your cart is empty.");

            // Make the order
            int orderId = -1;
            String insertOrderSql = "INSERT INTO Orders (user_id, total_amount, status) VALUES (?, ?, 'Pending')";
            try (PreparedStatement ps = conn.prepareStatement(insertOrderSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setDouble(2, totalAmount);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) orderId = rs.getInt(1);
            }

            String insertOrderItemSql = "INSERT INTO OrderItems (order_id, product_id, quantity, price_at_order) VALUES (?, ?, ?, ?)";
            String updateStockSql = "UPDATE Products SET stock = stock - ? WHERE id = ?";
            String clearCartSql = "DELETE FROM CartItems WHERE cart_id = ?";

            try (PreparedStatement psItem = conn.prepareStatement(insertOrderItemSql);
                 PreparedStatement psStock = conn.prepareStatement(updateStockSql);
                 PreparedStatement psClear = conn.prepareStatement(clearCartSql)) {

                for (OrderItemTemp item : itemsToOrder) {
                    psItem.setInt(1, orderId);
                    psItem.setInt(2, item.productId);
                    psItem.setInt(3, item.quantity);
                    psItem.setDouble(4, item.price);
                    psItem.addBatch();

                    psStock.setInt(1, item.quantity);
                    psStock.setInt(2, item.productId);
                    psStock.addBatch();
                }
                psItem.executeBatch();
                psStock.executeBatch();
                psClear.setInt(1, cartId);
                psClear.executeUpdate();
            }

            conn.commit();
            return new Order(orderId, userId, totalAmount, "Pending", new java.util.Date());

        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            throw new RuntimeException("Database error: " + e.getMessage());
        } finally {
            try { if (conn != null) { conn.setAutoCommit(true); conn.close(); } } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // Get order by id
    @Override
    public Order getOrderById(int orderId) {
        String sql = "SELECT id, user_id, total_amount, status, created_at FROM Orders WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, orderId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new Order(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getDouble("total_amount"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // If it does not exist
    }

    @Override
    public List<Order> getUserOrders(int userId) {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT * FROM Orders WHERE user_id = ? ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                orders.add(new Order(rs.getInt("id"), rs.getInt("user_id"), rs.getDouble("total_amount"), rs.getString("status"), rs.getTimestamp("created_at")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return orders;
    }

    private static class OrderItemTemp {
        int productId; int quantity; double price;
        OrderItemTemp(int p, int q, double pr) { this.productId = p; this.quantity = q; this.price = pr; }
    }
}