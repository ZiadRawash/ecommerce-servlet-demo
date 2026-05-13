package com.example.Interfaces;

import com.example.model.Order;
import java.util.List;

public interface IOrderService {
    Order placeOrder(int userId);
    List<Order> getUserOrders(int userId);
    Order getOrderById(int orderId);
}