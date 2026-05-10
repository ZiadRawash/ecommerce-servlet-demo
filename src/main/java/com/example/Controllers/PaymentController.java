package com.example.Controllers;

import com.example.DAO.PaymentDAO;
import com.example.Helpers.SecurityUtils;
import com.example.Implementation.PaymentFactory;
import com.example.Interfaces.IPaymentService;
import com.example.model.Payment;
import com.example.model.PaymentRequest;
import com.example.model.PaymentResponse;
import com.google.gson.Gson; // مكتبة لتحويل JSON لـ Object

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@WebServlet("/payments")
public class PaymentController extends HttpServlet {

    private PaymentDAO paymentDAO = new PaymentDAO();
    private Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setJsonResponse(response);

        try {
            Integer userId = SecurityUtils.getCurrentUserId(request);

            if (userId == null) {
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "{\"success\":false,\"message\":\"Login required\",\"data\":null}");
                return;
            }

            String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

            if (body == null || body.trim().isEmpty()) {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"message\":\"Request body is empty\",\"data\":null}");
                return;
            }


            PaymentRequest paymentRequest = gson.fromJson(body, PaymentRequest.class);

            if (paymentRequest.getOrderId() == 0) {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"message\":\"Order id is required\",\"data\":null}");
                return;
            }

            IPaymentService paymentService =
                    PaymentFactory.getPaymentService(paymentRequest.getPaymentMethod());

            PaymentResponse result =
                    paymentService.pay(userId, paymentRequest);

            String transactionIdJson = result.getTransactionId() == null
                    ? "null"
                    : "\"" + escapeJson(result.getTransactionId()) + "\"";

            int statusCode = result.isSuccess()
                    ? HttpServletResponse.SC_OK
                    : HttpServletResponse.SC_BAD_REQUEST;

            writeJson(response, statusCode,
                    "{"
                            + "\"success\":" + result.isSuccess() + ","
                            + "\"message\":\"" + escapeJson(result.getMessage()) + "\","
                            + "\"data\":{"
                            + "\"transactionId\":" + transactionIdJson
                            + "}"
                            + "}"
            );

        } catch (Exception e) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\",\"data\":null}");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setJsonResponse(response);

        try {
            Integer userId = SecurityUtils.getCurrentUserId(request);

            if (userId == null) {
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "{\"success\":false,\"message\":\"Login required\",\"data\":null}");
                return;
            }

            String transactionId = request.getParameter("transactionId");

            if (transactionId != null && !transactionId.trim().isEmpty()) {
                Payment payment =
                        paymentDAO.getByTransactionIdForUser(transactionId, userId);

                if (payment == null) {
                    writeJson(response, HttpServletResponse.SC_NOT_FOUND,
                            "{\"success\":false,\"message\":\"Payment not found\",\"data\":null}");
                    return;
                }

                writeJson(response, HttpServletResponse.SC_OK,
                        "{"
                                + "\"success\":true,"
                                + "\"message\":\"Payment found\","
                                + "\"data\":" + paymentToJson(payment)
                                + "}"
                );
                return;
            }

            List<Payment> payments = paymentDAO.getAllByUserId(userId);

            StringBuilder data = new StringBuilder();
            data.append("[");

            for (int i = 0; i < payments.size(); i++) {
                data.append(paymentToJson(payments.get(i)));

                if (i < payments.size() - 1) {
                    data.append(",");
                }
            }

            data.append("]");

            writeJson(response, HttpServletResponse.SC_OK,
                    "{"
                            + "\"success\":true,"
                            + "\"message\":\"Payments retrieved successfully\","
                            + "\"data\":" + data
                            + "}"
            );

        } catch (Exception e) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\",\"data\":null}");
        }
    }

    private void setJsonResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
    }

    private void writeJson(HttpServletResponse response, int statusCode, String json) throws IOException {
        response.setStatus(statusCode);
        response.getWriter().write(json);
    }

    private String paymentToJson(Payment payment) {
        return "{"
                + "\"orderId\":" + payment.getOrderId() + ","
                + "\"paymentMethod\":\"" + escapeJson(payment.getPaymentMethod()) + "\","
                + "\"status\":\"" + escapeJson(payment.getStatus()) + "\","
                + "\"transactionId\":\"" + escapeJson(payment.getTransactionId()) + "\""
                + "}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}