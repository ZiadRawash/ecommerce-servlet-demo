package com.example.Implementation;

import com.example.Interfaces.IPaymentService;
import com.example.model.PaymentRequest;
import com.example.model.PaymentResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class CashPaymentService implements IPaymentService {

    @Override
    public PaymentResponse pay(int userId, PaymentRequest request) {

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                int orderId = request.getOrderId();

                if (!isValidPendingOrder(conn, orderId, userId)) {
                    return new PaymentResponse(false, "Order not found, not yours, or not pending");
                }

                if (paymentExists(conn, orderId)) {
                    return new PaymentResponse(false, "Order already has payment");
                }

                String transactionId = UUID.randomUUID().toString();

                String insertPayment =
                        "INSERT INTO Payments (order_id, payment_method, status, transaction_id) " +
                                "VALUES (?, ?, ?, ?)";

                try (PreparedStatement stmt = conn.prepareStatement(insertPayment)) {
                    stmt.setInt(1, orderId);
                    stmt.setString(2, "Cash");
                    stmt.setString(3, "Pending");
                    stmt.setString(4, transactionId);
                    stmt.executeUpdate();
                }

                String updateOrder =
                        "UPDATE Orders SET status = ? WHERE id = ?";

                try (PreparedStatement stmt = conn.prepareStatement(updateOrder)) {
                    stmt.setString(1, "Pending");
                    stmt.setInt(2, orderId);
                    stmt.executeUpdate();
                }

                conn.commit();

                return new PaymentResponse(
                        true,
                        "Cash payment created - pay on delivery",
                        transactionId
                );

            } catch (Exception e) {
                conn.rollback();
                return new PaymentResponse(false, e.getMessage());
            }

        } catch (Exception e) {
            return new PaymentResponse(false, e.getMessage());
        }
    }

    private boolean isValidPendingOrder(Connection conn, int orderId, int userId) throws Exception {
        String sql =
                "SELECT id FROM Orders " +
                        "WHERE id = ? AND user_id = ? AND status = 'Pending'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            stmt.setInt(2, userId);

            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    private boolean paymentExists(Connection conn, int orderId) throws Exception {
        String sql = "SELECT id FROM Payments WHERE order_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);

            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }
}
