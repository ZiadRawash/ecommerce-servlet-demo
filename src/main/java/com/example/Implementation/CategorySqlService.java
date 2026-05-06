package com.example.Implementation;

import com.example.Interfaces.ICategorySqlService;
import com.example.model.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategorySqlService implements ICategorySqlService {

    @Override
    public Category createCategory(Category category) {
        String sql = "INSERT INTO Categories (name) VALUES (?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, category.getName());
            ps.executeUpdate();

            ResultSet generatedKeys = ps.getGeneratedKeys();
            if (generatedKeys.next()) {
                category.setId(generatedKeys.getInt(1));
            }

            return category;

        } catch (Exception e) {
            System.out.println("Failed to create category: " + e.getMessage());
            return null;
        }
    }

    @Override
    public Category updateCategory(int id, Category category) {
        String sql = "UPDATE Categories SET name = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, category.getName());
            ps.setInt(2, id);

            int rows = ps.executeUpdate();

            if (rows == 0) {
                return null;
            }

            category.setId(id);
            return category;

        } catch (Exception e) {
            System.out.println("Failed to update category: " + e.getMessage());
            return null;
        }
    }

    @Override
    public List<Category> getAllCategories() {
        List<Category> categories = new ArrayList<>();
        String sql = "SELECT id, name FROM Categories";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                categories.add(new Category(
                        rs.getInt("id"),
                        rs.getString("name")
                ));
            }

        } catch (Exception e) {
            System.out.println("Failed to fetch categories: " + e.getMessage());
        }

        return categories;
    }

    @Override
    public boolean deleteCategory(int id) {
        String sql = "DELETE FROM Categories WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            int rows = ps.executeUpdate();
            return rows > 0;

        } catch (Exception e) {
            System.out.println("Failed to delete category: " + e.getMessage());
            return false;
        }
    }
}