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

        Integer userId = SecurityUtils.getCurrentUserId(req);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Please log in");
            return;
        }

        try {
            List<Order> orders = orderService.getUserOrders(userId);
            mapper.writeValue(resp.getWriter(), orders);
        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch orders: " + e.getMessage());
        }
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Integer userId = SecurityUtils.getCurrentUserId(req);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Please log in");
            return;
        }

        try {
            Order newOrder = orderService.placeOrder(userId);

            if (newOrder == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Failed to place order. Cart might be empty or insufficient stock.");
                return;
            }

            resp.setStatus(HttpServletResponse.SC_CREATED);
            mapper.writeValue(resp.getWriter(), newOrder);

        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
        }
    }

    private void sendErrorResponse(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}