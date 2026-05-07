package com.example.Controllers;

import com.example.Helpers.SecurityUtils;
import com.example.Implementation.CartSqlService;
import com.example.Interfaces.ICartSqlService;
import com.example.model.Cart;
import com.example.model.CartItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.List;

@WebServlet("/api/cart/*")
public class CartController extends HttpServlet {

    private final ICartSqlService cartService = new CartSqlService();
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

        Cart cart = cartService.getCartByUserId(userId);
        if (cart == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve cart");
            return;
        }

        List<CartItem> items = cartService.getCartItems(cart.getId());

        String json = String.format("{\"cartId\": %d, \"userId\": %d, \"items\": %s}",
                cart.getId(), cart.getUserId(), mapper.writeValueAsString(items));
        resp.getWriter().write(json);
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

        String pathInfo = req.getPathInfo();
        if (!"/items".equals(pathInfo)) {
            sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found");
            return;
        }

        if (req.getContentType() == null || !req.getContentType().contains("application/json")) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json");
            return;
        }

        try {
            CartItem item = mapper.readValue(req.getReader(), CartItem.class);

            if (item == null || item.getProductId() <= 0 || item.getQuantity() <= 0) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid data: productId and quantity (> 0) are required");
                return;
            }

            Cart cart = cartService.getCartByUserId(userId);
            if (cart == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve cart");
                return;
            }

            CartItem added = cartService.addToCart(cart.getId(), item);
            if (added == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Product not found or failed to add to cart");
                return;
            }

            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write("{\"message\": \"Item added to cart\", \"cartItemId\": " + added.getId() + "}");

        } catch (JsonProcessingException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON or invalid data types provided");
        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Integer userId = SecurityUtils.getCurrentUserId(req);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Please log in");
            return;
        }

        try {
            int cartItemId = getItemIdFromPath(req);

            CartItem body = mapper.readValue(req.getReader(), CartItem.class);

            if (body == null || body.getQuantity() <= 0) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid data: quantity (> 0) is required");
                return;
            }

            Cart cart = cartService.getCartByUserId(userId);
            if (cart == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve cart");
                return;
            }

            List<CartItem> items = cartService.getCartItems(cart.getId());
            boolean belongsToUser = items.stream().anyMatch(i -> i.getId() == cartItemId);
            if (!belongsToUser) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Cart item not found");
                return;
            }

            CartItem updated = cartService.updateCartItem(cartItemId, body.getQuantity());

            if (updated == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Cart item not found");
                return;
            }

            resp.getWriter().write("{\"message\": \"Cart item updated successfully\"}");

        } catch (NumberFormatException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid cart item id");
        } catch (JsonProcessingException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON or invalid data types provided");
        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
        }
    }

     @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Integer userId = SecurityUtils.getCurrentUserId(req);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Please log in");
            return;
        }

        String pathInfo = req.getPathInfo();

        if (pathInfo == null || pathInfo.equals("/")) {
            Cart cart = cartService.getCartByUserId(userId);
            if (cart == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve cart");
                return;
            }

            boolean deleted = cartService.deleteCart(cart.getId());
            if (!deleted) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Cart not found");
                return;
            }

            resp.getWriter().write("{\"message\": \"Cart cleared successfully\"}");
            return;
        }

        try {
            int cartItemId = getItemIdFromPath(req);

            Cart cart = cartService.getCartByUserId(userId);
            if (cart == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve cart");
                return;
            }

            List<CartItem> items = cartService.getCartItems(cart.getId());
            boolean belongsToUser = items.stream().anyMatch(i -> i.getId() == cartItemId);
            if (!belongsToUser) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Cart item not found");
                return;
            }

            boolean deleted = cartService.deleteCartItem(cartItemId);
            if (!deleted) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Cart item not found");
                return;
            }

            resp.getWriter().write("{\"message\": \"Cart item removed successfully\"}");

        } catch (NumberFormatException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid cart item id");
        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
        }
    }

    private int getItemIdFromPath(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();

        if (pathInfo == null || !pathInfo.startsWith("/items/")) {
            throw new NumberFormatException("Missing or invalid path");
        }

        return Integer.parseInt(pathInfo.substring("/items/".length()));
    }

    private void sendErrorResponse(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
