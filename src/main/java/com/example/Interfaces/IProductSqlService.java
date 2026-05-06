package com.example.Interfaces;

import com.example.model.Product;

import java.util.List;

public interface IProductSqlService {

    Product createProduct(Product product);

    Product updateProduct(int id, Product product);

    Product getProductById(int id);

    List<Product> getAllProducts();

    boolean deleteProduct(int id);
}