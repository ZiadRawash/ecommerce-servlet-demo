package com.example.DAO;

import com.example.Implementation.DBConnection;
import com.example.model.Payment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class PaymentDAO {

    public List<Payment> getAllByUserId(int userId) throws Exception {
        String sql =
                "SELECT p.* " +
                        "FROM Payments p " +
                        "JOIN Orders o ON p.order_id = o.id " +
                        "WHERE o.user_id = ? " +
                        "ORDER BY p.payment_date DESC";

        List<Payment> payments = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                payments.add(mapPayment(rs));
            }
        }

        return payments;
    }

    public Payment getByTransactionIdForUser(String transactionId, int userId) throws Exception {
        String sql =
                "SELECT p.* " +
                        "FROM Payments p " +
                        "JOIN Orders o ON p.order_id = o.id " +
                        "WHERE p.transaction_id = ? AND o.user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, transactionId);
            stmt.setInt(2, userId);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapPayment(rs);
            }

            return null;
        }
    }

    private Payment mapPayment(ResultSet rs) throws Exception {
        Payment payment = new Payment();

        payment.setId(rs.getInt("id"));
        payment.setOrderId(rs.getInt("order_id"));
        payment.setPaymentMethod(rs.getString("payment_method"));
        payment.setStatus(rs.getString("status"));
        payment.setTransactionId(rs.getString("transaction_id"));

        return payment;
    }
}
