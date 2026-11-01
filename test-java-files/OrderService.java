public class OrderService {
    private PaymentService paymentService;

    public void placeOrder() {
        paymentService.processPayment();
    }
}
