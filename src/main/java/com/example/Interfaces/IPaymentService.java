package com.example.Interfaces;

import com.example.model.PaymentRequest;
import com.example.model.PaymentResponse;

public interface IPaymentService {
    PaymentResponse pay(int userId, PaymentRequest request);
}
