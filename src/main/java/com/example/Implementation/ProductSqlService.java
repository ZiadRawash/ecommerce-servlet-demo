package com.example.Implementation;

import com.example.Interfaces.IProductSqlService;
import com.example.model.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductSqlService implements IProductSqlService {

    private boolean categoryExists(int categoryId) {
        String sql = "SELECT id FROM Categories WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, categoryId);

            ResultSet rs = ps.executeQuery();
            return rs.next();

        } catch (Exception e) {
            System.out.println("Failed to check category: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Product createProduct(Product product) {
        if (!categoryExists(product.getCategoryId())) {
            return null;
        }

        String sql = "INSERT INTO Products (name, price, stock, category_id) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, product.getName());
            ps.setDouble(2, product.getPrice());
            ps.setInt(3, product.getStock());
            ps.setInt(4, product.getCategoryId());

            ps.executeUpdate();

            ResultSet generatedKeys = ps.getGeneratedKeys();
            if (generatedKeys.next()) {
                product.setId(generatedKeys.getInt(1));
            }

            return product;

        } catch (Exception e) {
            System.out.println("Failed to create product: " + e.getMessage());
            return null;
        }
    }

    @Override
    public Product updateProduct(int id, Product product) {
        if (!categoryExists(product.getCategoryId())) {
            return null;
        }

        String sql = "UPDATE Products SET name = ?, price = ?, stock = ?, category_id = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, product.getName());
            ps.setDouble(2, product.getPrice());
            ps.setInt(3, product.getStock());
            ps.setInt(4, product.getCategoryId());
            ps.setInt(5, id);

            int rows = ps.executeUpdate();

            if (rows == 0) {
                return null;
            }

            product.setId(id);
            return product;

        } catch (Exception e) {
            System.out.println("Failed to update product: " + e.getMessage());
            return null;
        }
    }

    @Override
    public Product getProductById(int id) {
        String sql = "SELECT id, name, price, stock, category_id FROM Products WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new Product(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getInt("stock"),
                        rs.getInt("category_id")
                );
            }

            return null;

        } catch (Exception e) {
            System.out.println("Failed to fetch product: " + e.getMessage());
            return null;
        }
    }

    @Override
    public List<Product> getAllProducts() {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT id, name, price, stock, category_id FROM Products";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
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

        } catch (Exception e) {
            System.out.println("Failed to fetch products: " + e.getMessage());
        }

        return products;
    }

    @Override
    public boolean deleteProduct(int id) {
        String sql = "DELETE FROM Products WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            int rows = ps.executeUpdate();
            return rows > 0;

        } catch (Exception e) {
            System.out.println("Failed to delete product: " + e.getMessage());
            return false;
        }
    }
}