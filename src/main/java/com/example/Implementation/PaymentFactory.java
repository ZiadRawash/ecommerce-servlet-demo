package com.example.Implementation;

import com.example.Interfaces.IPaymentService;

public class PaymentFactory {

    public static IPaymentService getPaymentService(String paymentMethod) {

        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            throw new RuntimeException("Payment method is required");
        }

        if (paymentMethod.equalsIgnoreCase("Cash")) {
            return new CashPaymentService();
        }

        if (paymentMethod.equalsIgnoreCase("Visa")
                || paymentMethod.equalsIgnoreCase("Card")) {
            return new VisaPaymentService();
        }

        throw new RuntimeException("Invalid payment method");
    }
}
