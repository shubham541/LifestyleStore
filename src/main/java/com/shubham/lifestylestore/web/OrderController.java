package com.shubham.lifestylestore.web;

import com.shubham.lifestylestore.domain.Order;
import com.shubham.lifestylestore.service.OrderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /** Places an order from the caller's own cart; the request has no body so prices cannot be tampered with. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order place(@AuthenticationPrincipal Jwt jwt) {
        return orders.placeOrder(jwt.getSubject());
    }

    @GetMapping
    public List<Order> history(@AuthenticationPrincipal Jwt jwt) {
        return orders.history(jwt.getSubject());
    }
}
