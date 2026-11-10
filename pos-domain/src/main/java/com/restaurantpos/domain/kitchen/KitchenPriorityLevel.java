package com.restaurantpos.domain.kitchen;

public enum KitchenPriorityLevel {
    NORMAL(0),
    HIGH(10),
    URGENT(20);

    private final int score;

    KitchenPriorityLevel(int score) {
        this.score = score;
    }

    public int score() {
        return score;
    }
}
