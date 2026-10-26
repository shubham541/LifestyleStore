package com.shubham.lifestylestore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import com.shubham.lifestylestore.domain.Cart;
import com.shubham.lifestylestore.domain.Cart.CartItem;
import com.shubham.lifestylestore.domain.Category;
import com.shubham.lifestylestore.domain.Order;
import com.shubham.lifestylestore.domain.Product;
import com.shubham.lifestylestore.domain.UserAccount;
import com.shubham.lifestylestore.repo.OrderRepository;
import com.shubham.lifestylestore.repo.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

class OrderServiceTest {

    private final CartService carts = mock(CartService.class);
    private final ProductService products = mock(ProductService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private OrderService service;

    private final Product camera = new Product("cam", "Camera", "Canon", "d", Category.CAMERAS, 10_000_00, 12_000_00,
            17, 4.5, 10, List.of(), 5, "/i", null);
    private final Product watch = new Product("wat", "Watch", "Titan", "d", Category.WATCHES, 2_000_00, 2_500_00,
            20, 4.2, 10, List.of(), 5, "/i", null);

    @BeforeEach
    void setUp() {
        service = new OrderService(carts, products, orders, users, mongo);
        when(users.findById("u1")).thenReturn(Optional.of(new UserAccount("u1", "A", "a@b.co", "h", "999", "Pune",
                "1 Main", 0, null, Instant.now())));
        when(products.get("cam")).thenReturn(camera);
        when(products.get("wat")).thenReturn(watch);
        when(orders.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static UpdateResult modified(long n) {
        return UpdateResult.acknowledged(n, n, null);
    }

    @Test
    void totalIsComputedFromCatalogueAndCartIsCleared() {
        when(carts.load("u1")).thenReturn(new Cart("c", "u1", List.of(new CartItem("cam", 1), new CartItem("wat", 3)),
                Instant.now()));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Product.class)))
                .thenReturn(modified(1));

        Order order = service.placeOrder("u1");

        assertThat(order.subtotalPaise()).isEqualTo(10_000_00 + 3 * 2_000_00);
        assertThat(order.shippingPaise()).isZero(); // above the free-shipping threshold
        assertThat(order.totalPaise()).isEqualTo(order.subtotalPaise());
        assertThat(order.shippingAddress()).isEqualTo("1 Main, Pune");
        verify(carts).clear("u1");
    }

    @Test
    void smallOrdersPayShipping() {
        var cheap = new Product("pen", "Pen", "Cello", "d", Category.BOOKS, 100_00, 100_00, 0, 4, 1, List.of(), 5, null, null);
        when(products.get("pen")).thenReturn(cheap);
        when(carts.load("u1")).thenReturn(new Cart("c", "u1", List.of(new CartItem("pen", 1)), Instant.now()));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Product.class)))
                .thenReturn(modified(1));

        Order order = service.placeOrder("u1");

        assertThat(order.shippingPaise()).isEqualTo(Pricing.SHIPPING_FEE_PAISE);
        assertThat(order.totalPaise()).isEqualTo(100_00 + Pricing.SHIPPING_FEE_PAISE);
    }

    @Test
    void insufficientStockReleasesAlreadyReservedItemsAndKeepsCart() {
        when(carts.load("u1")).thenReturn(new Cart("c", "u1", List.of(new CartItem("cam", 1), new CartItem("wat", 9)),
                Instant.now()));
        // first reserve succeeds, second fails, then one compensating release
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Product.class)))
                .thenReturn(modified(1), modified(0), modified(1));

        assertThatThrownBy(() -> service.placeOrder("u1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getMessage()).contains("Watch"));

        verify(mongo, times(3)).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Product.class));
        verify(orders, never()).save(any());
        verify(carts, never()).clear(any());
    }

    @Test
    void legacyOrdersWithoutShippingFieldsStillLoad() {
        // stored before subtotal/shipping existed: both are null when read back
        var legacy = new Order("o1", "u1", List.of(new Order.OrderItem("cam", "Camera", 10_000_00, 2)), null, null,
                20_000_00, "PLACED", "Old Town", Instant.now());
        when(orders.findByUserIdOrderByCreatedAtDesc("u1")).thenReturn(List.of(legacy));

        Order shown = service.history("u1").get(0);

        assertThat(shown.subtotalPaise()).isEqualTo(20_000_00);
        assertThat(shown.shippingPaise()).isZero();
        assertThat(shown.totalPaise()).isEqualTo(20_000_00);
    }

    @Test
    void emptyCartIsRejected() {
        when(carts.load("u1")).thenReturn(new Cart("c", "u1", List.of(), Instant.now()));

        assertThatThrownBy(() -> service.placeOrder("u1")).isInstanceOf(ApiException.class);
    }
}
