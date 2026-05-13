package com.example.Controllers;

import com.example.Helpers.SecurityUtils;
import com.example.Implementation.OrderSqlService;
import com.example.Interfaces.IOrderService;
import com.example.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.List;

@WebServlet("/api/orders/*")
public class OrderController extends HttpServlet {

    private final IOrderService orderService = new OrderSqlService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Integer currentUserId = SecurityUtils.getCurrentUserId(req);
        if (currentUserId == null) {
            sendErrorResponse(resp, 401, "Please log in first.");
            return;
        }

        String pathInfo = req.getPathInfo();

        if (pathInfo == null || pathInfo.equals("/")) {
            List<Order> orders = orderService.getUserOrders(currentUserId);
            mapper.writeValue(resp.getWriter(), orders);
            return;
        }

        try {
            int orderId = Integer.parseInt(pathInfo.substring(1));
            Order order = orderService.getOrderById(orderId);

            // ID does not exist
            if (order == null || order.getUserId() != currentUserId) {
                sendErrorResponse(resp, 404, "Order not found or access denied.");
                return;
            }

            mapper.writeValue(resp.getWriter(), order);

        } catch (NumberFormatException e) {
            sendErrorResponse(resp, 400, "Invalid Order ID format.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Integer userId = SecurityUtils.getCurrentUserId(req);
        if (userId == null) {
            sendErrorResponse(resp, 401, "Unauthorized.");
            return;
        }

        try {
            Order newOrder = orderService.placeOrder(userId);
            resp.setStatus(201);
            mapper.writeValue(resp.getWriter(), newOrder);
        } catch (RuntimeException e) {
            sendErrorResponse(resp, 400, e.getMessage());
        } catch (Exception e) {
            sendErrorResponse(resp, 500, "An unexpected error occurred.");
        }
    }

    private void sendErrorResponse(HttpServletResponse resp, int code, String msg) throws IOException {
        resp.setStatus(code);
        resp.getWriter().write("{\"error\": \"" + msg + "\"}");
    }
}