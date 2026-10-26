package com.shubham.lifestylestore.service;

import com.shubham.lifestylestore.domain.Order;
import com.shubham.lifestylestore.domain.Order.OrderItem;
import com.shubham.lifestylestore.domain.Product;
import com.shubham.lifestylestore.domain.UserAccount;
import com.shubham.lifestylestore.repo.OrderRepository;
import com.shubham.lifestylestore.repo.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final CartService cartService;
    private final ProductService productService;
    private final OrderRepository orders;
    private final UserRepository users;
    private final MongoTemplate mongo;

    public OrderService(CartService cartService, ProductService productService, OrderRepository orders,
                        UserRepository users, MongoTemplate mongo) {
        this.cartService = cartService;
        this.productService = productService;
        this.orders = orders;
        this.users = users;
        this.mongo = mongo;
    }

    /**
     * Prices and totals are always recomputed from the catalogue; nothing price-related is accepted from the
     * client. Stock is decremented with an atomic conditional update per line and compensated on failure
     * (works on a standalone MongoDB, no multi-document transaction needed).
     */
    public Order placeOrder(String userId) {
        var cart = cartService.load(userId);
        if (cart.items().isEmpty()) {
            throw ApiException.badRequest("Your cart is empty");
        }
        UserAccount user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));

        List<OrderItem> lines = new ArrayList<>();
        List<OrderItem> reserved = new ArrayList<>();
        long total = 0;
        try {
            for (var item : cart.items()) {
                Product p = productService.get(item.productId());
                if (!reserveStock(p.id(), item.quantity())) {
                    throw ApiException.conflict("Not enough stock for " + p.name());
                }
                var line = new OrderItem(p.id(), p.name(), p.pricePaise(), item.quantity());
                reserved.add(line);
                lines.add(line);
                total += p.pricePaise() * item.quantity();
            }
        } catch (RuntimeException e) {
            reserved.forEach(l -> releaseStock(l.productId(), l.quantity()));
            throw e;
        }

        String address = "%s, %s".formatted(user.address(), user.city());
        long shipping = Pricing.shippingFor(total);
        Order order = orders.save(new Order(UUID.randomUUID().toString(), userId, lines, total, shipping,
                total + shipping, "PLACED", address, Instant.now()));
        cartService.clear(userId);
        log.info("Order {} placed by {} total={} paise", order.id(), userId, order.totalPaise());
        return order;
    }

    public List<Order> history(String userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId).stream().map(Order::withDerivedTotals).toList();
    }

    private boolean reserveStock(String productId, int quantity) {
        var result = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(productId).and("stock").gte(quantity)),
                new Update().inc("stock", -quantity), Product.class);
        return result.getModifiedCount() == 1;
    }

    private void releaseStock(String productId, int quantity) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(productId)),
                new Update().inc("stock", quantity), Product.class);
    }
}
