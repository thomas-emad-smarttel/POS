package com.restaurantpos.domain.kitchen;

import com.restaurantpos.domain.exception.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central domain policy governing kitchen ticket & item state machines, partial readiness,
 * overdue detection, and Expo multi-station order readiness aggregation.
 */
public final class KitchenTicketStatePolicy {

    private static final Map<KitchenTicketStatus, Set<KitchenTicketStatus>> TICKET_TRANSITIONS = Map.of(
            KitchenTicketStatus.NEW, EnumSet.of(
                    KitchenTicketStatus.QUEUED, KitchenTicketStatus.ACCEPTED,
                    KitchenTicketStatus.PREPARING, KitchenTicketStatus.ON_HOLD,
                    KitchenTicketStatus.CANCELLED),
            KitchenTicketStatus.QUEUED, EnumSet.of(
                    KitchenTicketStatus.ACCEPTED, KitchenTicketStatus.PREPARING,
                    KitchenTicketStatus.ON_HOLD, KitchenTicketStatus.CANCELLED),
            KitchenTicketStatus.ACCEPTED, EnumSet.of(
                    KitchenTicketStatus.PREPARING, KitchenTicketStatus.READY,
                    KitchenTicketStatus.ON_HOLD, KitchenTicketStatus.CANCELLED),
            KitchenTicketStatus.IN_PROGRESS, EnumSet.of(
                    KitchenTicketStatus.PREPARING, KitchenTicketStatus.READY,
                    KitchenTicketStatus.ON_HOLD, KitchenTicketStatus.CANCELLED),
            KitchenTicketStatus.PREPARING, EnumSet.of(
                    KitchenTicketStatus.READY, KitchenTicketStatus.ON_HOLD,
                    KitchenTicketStatus.CANCELLED),
            KitchenTicketStatus.ON_HOLD, EnumSet.of(
                    KitchenTicketStatus.QUEUED, KitchenTicketStatus.ACCEPTED,
                    KitchenTicketStatus.PREPARING, KitchenTicketStatus.CANCELLED),
            KitchenTicketStatus.READY, EnumSet.of(
                    KitchenTicketStatus.SERVED, KitchenTicketStatus.BUMPED,
                    KitchenTicketStatus.CANCELLED),
            KitchenTicketStatus.SERVED, EnumSet.noneOf(KitchenTicketStatus.class),
            KitchenTicketStatus.BUMPED, EnumSet.noneOf(KitchenTicketStatus.class),
            KitchenTicketStatus.CANCELLED, EnumSet.noneOf(KitchenTicketStatus.class)
    );

    private static final Map<KitchenTicketItemStatus, Set<KitchenTicketItemStatus>> ITEM_TRANSITIONS = Map.of(
            KitchenTicketItemStatus.NEW, EnumSet.of(
                    KitchenTicketItemStatus.QUEUED, KitchenTicketItemStatus.PREPARING,
                    KitchenTicketItemStatus.READY, KitchenTicketItemStatus.CANCELLED),
            KitchenTicketItemStatus.QUEUED, EnumSet.of(
                    KitchenTicketItemStatus.PREPARING, KitchenTicketItemStatus.READY,
                    KitchenTicketItemStatus.CANCELLED),
            KitchenTicketItemStatus.IN_PROGRESS, EnumSet.of(
                    KitchenTicketItemStatus.PREPARING, KitchenTicketItemStatus.READY,
                    KitchenTicketItemStatus.CANCELLED),
            KitchenTicketItemStatus.PREPARING, EnumSet.of(
                    KitchenTicketItemStatus.READY, KitchenTicketItemStatus.CANCELLED),
            KitchenTicketItemStatus.READY, EnumSet.of(
                    KitchenTicketItemStatus.SERVED, KitchenTicketItemStatus.CANCELLED),
            KitchenTicketItemStatus.SERVED, EnumSet.noneOf(KitchenTicketItemStatus.class),
            KitchenTicketItemStatus.CANCELLED, EnumSet.noneOf(KitchenTicketItemStatus.class),
            KitchenTicketItemStatus.VOIDED, EnumSet.noneOf(KitchenTicketItemStatus.class)
    );

    private KitchenTicketStatePolicy() {
    }

    public static void requireTicketTransition(KitchenTicketStatus from, KitchenTicketStatus to) {
        if (from == to) {
            return;
        }
        if (!TICKET_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new DomainException(
                    "INVALID_KITCHEN_TICKET_TRANSITION",
                    "Invalid kitchen ticket transition from " + from + " to " + to
            );
        }
    }

    public static void requireItemTransition(KitchenTicketItemStatus from, KitchenTicketItemStatus to) {
        if (from == to) {
            return;
        }
        if (!ITEM_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new DomainException(
                    "INVALID_KITCHEN_ITEM_TRANSITION",
                    "Invalid kitchen item transition from " + from + " to " + to
            );
        }
    }

    public static boolean areAllActiveItemsReady(List<KitchenTicketItem> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        boolean hasActiveItem = false;
        for (KitchenTicketItem item : items) {
            if (!item.status().isCancelledOrVoided()) {
                hasActiveItem = true;
                if (!item.status().satisfiesTicketReadiness()) {
                    return false;
                }
            }
        }
        return hasActiveItem;
    }

    public static boolean areAllActiveItemsCancelled(List<KitchenTicketItem> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        return items.stream().allMatch(i -> i.status().isCancelledOrVoided());
    }

    public static long elapsedSeconds(Instant firedAt, Instant now) {
        if (firedAt == null || now == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(firedAt, now).getSeconds());
    }

    public static boolean isOverdue(KitchenTicketStatus status, Instant firedAt, int expectedPrepMinutes, Instant now) {
        if (status.isTerminal() || status == KitchenTicketStatus.READY || status == KitchenTicketStatus.ON_HOLD) {
            return false;
        }
        return elapsedSeconds(firedAt, now) > expectedPrepMinutes * 60L;
    }

    public static boolean isExpoOrderReady(List<KitchenTicket> orderTickets) {
        if (orderTickets == null || orderTickets.isEmpty()) {
            return false;
        }
        boolean hasNonCancelled = false;
        for (KitchenTicket ticket : orderTickets) {
            if (ticket.status() == KitchenTicketStatus.CANCELLED) {
                continue;
            }
            hasNonCancelled = true;
            if (ticket.status() != KitchenTicketStatus.READY
                    && ticket.status() != KitchenTicketStatus.SERVED
                    && ticket.status() != KitchenTicketStatus.BUMPED) {
                return false;
            }
        }
        return hasNonCancelled;
    }
}
