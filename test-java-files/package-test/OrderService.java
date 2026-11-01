package com.shop.order;

import com.shop.payment.PaymentService;

public class OrderService {
    private PaymentService paymentService;

    public void placeOrder() {
        paymentService.processPayment();
    }
}
