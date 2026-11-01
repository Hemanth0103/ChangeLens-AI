package com.shop.services;

public class PaymentService extends BaseService
        implements PaymentProcessor {

    @Override
    public void processPayment() {
        log();
        System.out.println("Payment processed");
    }
}
