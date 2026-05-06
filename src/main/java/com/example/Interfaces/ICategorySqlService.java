package com.example.Interfaces;

import com.example.model.Category;

import java.util.List;

public interface ICategorySqlService {

    Category createCategory(Category category);

    Category updateCategory(int id, Category category);

    List<Category> getAllCategories();

    boolean deleteCategory(int id);
}