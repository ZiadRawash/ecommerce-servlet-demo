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

            int cartId = -1;
            String cartSql = "SELECT id FROM Cart WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(cartSql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    cartId = rs.getInt("id");
                } else {
                    return null;
                }
            }


            String itemsSql = "SELECT ci.id as cart_item_id, ci.product_id, ci.quantity, p.price, p.stock " +
                    "FROM CartItems ci " +
                    "JOIN Products p ON ci.product_id = p.id " +
                    "WHERE ci.cart_id = ?";

            double totalAmount = 0;
            List<OrderItemTemp> itemsToOrder = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(itemsSql)) {
                ps.setInt(1, cartId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int productId = rs.getInt("product_id");
                    int quantity = rs.getInt("quantity");
                    double price = rs.getDouble("price");
                    int stock = rs.getInt("stock");

                    if (quantity > stock) {
                        conn.rollback();
                        System.out.println("Insufficient stock for product ID: " + productId);
                        return null;
                    }

                    totalAmount += (price * quantity);
                    itemsToOrder.add(new OrderItemTemp(productId, quantity, price));
                }
            }

            if (itemsToOrder.isEmpty()) {
                conn.rollback();
                return null;
            }


            int orderId = -1;
            String insertOrderSql = "INSERT INTO Orders (user_id, total_amount, status) VALUES (?, ?, 'Pending')";
            try (PreparedStatement ps = conn.prepareStatement(insertOrderSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setDouble(2, totalAmount);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    orderId = rs.getInt(1);
                }
            }


            String insertOrderItemSql = "INSERT INTO OrderItems (order_id, product_id, quantity, price_at_order) VALUES (?, ?, ?, ?)";
            String updateStockSql = "UPDATE Products SET stock = stock - ? WHERE id = ?";
            String clearCartSql = "DELETE FROM CartItems WHERE cart_id = ?";

            try (PreparedStatement psInsertItem = conn.prepareStatement(insertOrderItemSql);
                 PreparedStatement psUpdateStock = conn.prepareStatement(updateStockSql);
                 PreparedStatement psClearCart = conn.prepareStatement(clearCartSql)) {

                for (OrderItemTemp item : itemsToOrder) {

                    psInsertItem.setInt(1, orderId);
                    psInsertItem.setInt(2, item.productId);
                    psInsertItem.setInt(3, item.quantity);
                    psInsertItem.setDouble(4, item.price);
                    psInsertItem.addBatch();


                    psUpdateStock.setInt(1, item.quantity);
                    psUpdateStock.setInt(2, item.productId);
                    psUpdateStock.addBatch();
                }

                psInsertItem.executeBatch();
                psUpdateStock.executeBatch();


                psClearCart.setInt(1, cartId);
                psClearCart.executeUpdate();
            }


            conn.commit();


            return new Order(orderId, userId, totalAmount, "Pending", new java.util.Date());

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public List<Order> getUserOrders(int userId) {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT id, user_id, total_amount, status, created_at FROM Orders WHERE user_id = ? ORDER BY created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                orders.add(new Order(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getDouble("total_amount"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orders;
    }


    private static class OrderItemTemp {
        int productId;
        int quantity;
        double price;

        OrderItemTemp(int productId, int quantity, double price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }
    }
}