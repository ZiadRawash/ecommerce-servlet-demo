package com.example.Implementation;

import com.example.Interfaces.ICartSqlService;
import com.example.model.Cart;
import com.example.model.CartItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CartSqlService implements ICartSqlService {

    // Helper method: Gets an existing cart or creates a new one for the user
    private Cart getOrCreateCart(int userId) {
        String selectSql = "SELECT id, user_id FROM Cart WHERE user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new Cart(rs.getInt("id"), rs.getInt("user_id"));
            }

            // If no cart exists, create one
            String insertSql = "INSERT INTO Cart (user_id) VALUES (?)";
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                insertPs.setInt(1, userId);
                insertPs.executeUpdate();

                ResultSet keys = insertPs.getGeneratedKeys();
                if (keys.next()) {
                    return new Cart(keys.getInt(1), userId);
                }
            }

        } catch (SQLException e) {
            System.err.println("Failed to get or create cart: " + e.getMessage());
        }
        return null;
    }

    // Helper method: Checks if a product exists before adding to cart
    private boolean productExists(int productId) {
        String sql = "SELECT id FROM Products WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            System.err.println("Failed to check product: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Cart getCartByUserId(int userId) {
        return getOrCreateCart(userId);
    }

    @Override
    public boolean deleteCart(int cartId) {
        String sql = "DELETE FROM Cart WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, cartId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Failed to delete cart: " + e.getMessage());
            return false;
        }
    }

    @Override
    public CartItem addToCart(int cartId, CartItem item) {
        // 1. Check if product exists AND get current stock
        String stockSql = "SELECT stock FROM Products WHERE id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            int currentStock = 0;
            try (PreparedStatement ps = conn.prepareStatement(stockSql)) {
                ps.setInt(1, item.getProductId());
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) return null; // Product doesn't exist
                currentStock = rs.getInt("stock");
            }

            // 2. Check if item is already in cart to calculate "target" quantity
            String checkSql = "SELECT id, quantity FROM CartItems WHERE cart_id = ? AND product_id = ?";
            int existingId = -1;
            int currentCartQuantity = 0;

            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setInt(1, cartId);
                checkPs.setInt(2, item.getProductId());
                ResultSet rs = checkPs.executeQuery();
                if (rs.next()) {
                    existingId = rs.getInt("id");
                    currentCartQuantity = rs.getInt("quantity");
                }
            }

            int totalRequestedQuantity = currentCartQuantity + item.getQuantity();

            // 3. Validation: Prevent adding more than available stock
            if (totalRequestedQuantity > currentStock) {
                System.out.println("Requested quantity exceeds stock!");
                return null;
            }

            if (existingId != -1) {
                // Update existing row
                String updateSql = "UPDATE CartItems SET quantity = ? WHERE id = ?";
                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setInt(1, totalRequestedQuantity);
                    updatePs.setInt(2, existingId);
                    updatePs.executeUpdate();
                }
                item.setId(existingId);
                item.setQuantity(totalRequestedQuantity);
            } else {
                // Insert new row
                String insertSql = "INSERT INTO CartItems (cart_id, product_id, quantity) VALUES (?, ?, ?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    insertPs.setInt(1, cartId);
                    insertPs.setInt(2, item.getProductId());
                    insertPs.setInt(3, item.getQuantity());
                    insertPs.executeUpdate();
                    ResultSet keys = insertPs.getGeneratedKeys();
                    if (keys.next()) item.setId(keys.getInt(1));
                }
            }
            item.setCartId(cartId);
            return item;

        } catch (SQLException e) {
            System.err.println("Failed to add to cart: " + e.getMessage());
            return null;
        }
    }

    @Override
    public CartItem updateCartItem(int cartItemId, int quantity) {
        // 1. Get the product_id for this cart item and the current stock
        String stockCheckSql = "SELECT p.stock FROM Products p " +
                "JOIN CartItems ci ON p.id = ci.product_id " +
                "WHERE ci.id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            // Validate stock
            try (PreparedStatement ps = conn.prepareStatement(stockCheckSql)) {
                ps.setInt(1, cartItemId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    int stock = rs.getInt("stock");
                    if (quantity > stock) {
                        System.out.println("Update failed: Quantity exceeds stock.");
                        return null;
                    }
                } else {
                    return null; // Cart item not found
                }
            }

            // 2. Proceed with update if stock is sufficient
            String updateSql = "UPDATE CartItems SET quantity = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, quantity);
                ps.setInt(2, cartItemId);
                if (ps.executeUpdate() == 0) return null;
            }

            // 3. Fetch updated object
            String selectSql = "SELECT id, cart_id, product_id, quantity FROM CartItems WHERE id = ?";
            try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                selectPs.setInt(1, cartItemId);
                ResultSet rs = selectPs.executeQuery();
                if (rs.next()) {
                    return new CartItem(rs.getInt("id"), rs.getInt("cart_id"),
                            rs.getInt("product_id"), rs.getInt("quantity"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to update cart item: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<CartItem> getCartItems(int cartId) {
        List<CartItem> items = new ArrayList<>();
        String sql = "SELECT id, cart_id, product_id, quantity FROM CartItems WHERE cart_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, cartId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                items.add(new CartItem(
                        rs.getInt("id"),
                        rs.getInt("cart_id"),
                        rs.getInt("product_id"),
                        rs.getInt("quantity")
                ));
            }

        } catch (SQLException e) {
            System.err.println("Failed to fetch cart items: " + e.getMessage());
        }

        return items;
    }

    @Override
    public boolean deleteCartItem(int cartItemId) {
        String sql = "DELETE FROM CartItems WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, cartItemId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Failed to delete cart item: " + e.getMessage());
            return false;
        }
    }
}