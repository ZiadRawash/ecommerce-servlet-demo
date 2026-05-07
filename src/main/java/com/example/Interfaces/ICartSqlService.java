package com.example.Interfaces;

import com.example.model.Cart;
import com.example.model.CartItem;

import java.util.List;

public interface ICartSqlService {

    Cart getCartByUserId(int userId);

    boolean deleteCart(int cartId);

    CartItem addToCart(int cartId, CartItem item);

    CartItem updateCartItem(int cartItemId, int quantity);

    List<CartItem> getCartItems(int cartId);

    boolean deleteCartItem(int cartItemId);
}
