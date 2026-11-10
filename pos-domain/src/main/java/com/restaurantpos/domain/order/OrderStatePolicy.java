package com.restaurantpos.domain.order;

import com.restaurantpos.domain.exception.DomainException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces valid order status transitions and item mutability rules.
 */
public final class OrderStatePolicy {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.DRAFT, EnumSet.of(OrderStatus.OPEN, OrderStatus.ON_HOLD, OrderStatus.IN_KITCHEN, OrderStatus.CANCELLED),
            OrderStatus.OPEN, EnumSet.of(OrderStatus.ON_HOLD, OrderStatus.IN_KITCHEN, OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.ON_HOLD, EnumSet.of(OrderStatus.OPEN, OrderStatus.IN_KITCHEN, OrderStatus.CANCELLED),
            OrderStatus.IN_KITCHEN, EnumSet.of(OrderStatus.READY, OrderStatus.SERVED, OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.READY, EnumSet.of(OrderStatus.SERVED, OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.SERVED, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, EnumSet.of(OrderStatus.PARTIALLY_REFUNDED, OrderStatus.REFUNDED),
            OrderStatus.PARTIALLY_REFUNDED, EnumSet.of(OrderStatus.PARTIALLY_REFUNDED, OrderStatus.REFUNDED),
            OrderStatus.CANCELLED, EnumSet.of(OrderStatus.OPEN),
            OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class)
    );

    private OrderStatePolicy() {
    }

    public static boolean isMutableForItems(OrderStatus status) {
        return status == OrderStatus.DRAFT
                || status == OrderStatus.OPEN
                || status == OrderStatus.ON_HOLD
                || status == OrderStatus.IN_KITCHEN;
    }

    public static void requireMutableForItems(OrderStatus status) {
        if (!isMutableForItems(status)) {
            throw new DomainException("ORDER_NOT_MUTABLE", "Order in status " + status + " cannot be modified");
        }
    }

    public static void requireTransition(OrderStatus from, OrderStatus to) {
        if (from == to) {
            return;
        }
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new DomainException("INVALID_ORDER_TRANSITION", "Invalid order transition from " + from + " to " + to);
        }
    }
}
