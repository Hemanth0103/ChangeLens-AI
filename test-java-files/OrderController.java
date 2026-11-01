public class OrderController {
    private OrderService orderService;

    public void createOrder() {
        orderService.placeOrder();
    }
}
