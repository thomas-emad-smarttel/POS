package com.restaurantpos.domain.security;

import com.restaurantpos.shared.security.PermissionCode;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

/**
 * Default RBAC role templates seeded idempotently for new tenants.
 * Administrators can freely customize non-protected roles without upgrades overwriting them.
 */
public enum DefaultRoleTemplate {
    SUPER_ADMIN(
            "Super Admin",
            "Full system administration across all branches and security settings",
            "TENANT",
            new BigDecimal("100.00"),
            EnumSet.allOf(PermissionCode.class)
    ),
    RESTAURANT_OWNER(
            "Restaurant Owner",
            "Tenant-wide ownership over branches, finances, users, roles, and reports",
            "TENANT",
            new BigDecimal("100.00"),
            EnumSet.allOf(PermissionCode.class)
    ),
    BRANCH_MANAGER(
            "Branch Manager",
            "Full operational and staff management within assigned branches",
            "BRANCH",
            new BigDecimal("50.00"),
            EnumSet.of(
                    PermissionCode.DASHBOARD_VIEW,
                    PermissionCode.ORDERS_VIEW, PermissionCode.ORDERS_CREATE, PermissionCode.ORDERS_EDIT,
                    PermissionCode.ORDERS_UPDATE, PermissionCode.ORDERS_CANCEL,
                    PermissionCode.ORDERS_TRANSFER, PermissionCode.ORDERS_MERGE, PermissionCode.ORDERS_SPLIT,
                    PermissionCode.ORDERS_DISCOUNT, PermissionCode.ORDERS_DISCOUNT_APPLY, PermissionCode.ORDERS_DISCOUNT_APPROVE,
                    PermissionCode.PAYMENTS_VIEW, PermissionCode.PAYMENTS_PROCESS, PermissionCode.PAYMENTS_CREATE,
                    PermissionCode.PAYMENTS_REFUND, PermissionCode.PAYMENTS_REVERSE, PermissionCode.PAYMENTS_VOID,
                    PermissionCode.CASH_OPEN, PermissionCode.CASH_CLOSE, PermissionCode.CASH_SESSION_OPEN,
                    PermissionCode.CASH_SESSION_CLOSE, PermissionCode.CASH_SESSION_RECONCILE,
                    PermissionCode.CASH_SESSION_REOPEN, PermissionCode.CASH_MOVEMENT_CREATE,
                    PermissionCode.CASH_DRAWER_OPEN, PermissionCode.CASH_DRAWER_OPEN_WITHOUT_SALE,
                    PermissionCode.KITCHEN_VIEW, PermissionCode.KITCHEN_UPDATE, PermissionCode.KITCHEN_MANAGE,
                    PermissionCode.KITCHEN_STATION_MANAGE, PermissionCode.KITCHEN_ROUTING_MANAGE,
                    PermissionCode.KITCHEN_TICKET_ACCEPT, PermissionCode.KITCHEN_TICKET_START,
                    PermissionCode.KITCHEN_TICKET_READY, PermissionCode.KITCHEN_TICKET_CANCEL,
                    PermissionCode.KITCHEN_TICKET_HOLD, PermissionCode.KITCHEN_TICKET_REFIRE,
                    PermissionCode.KITCHEN_TICKET_SERVE, PermissionCode.KITCHEN_PRINTER_MANAGE,
                    PermissionCode.KITCHEN_EXPO_VIEW,
                    PermissionCode.TABLES_VIEW, PermissionCode.TABLES_MANAGE,
                    PermissionCode.MENU_VIEW, PermissionCode.MENU_CREATE, PermissionCode.MENU_UPDATE, PermissionCode.MENU_MANAGE,
                    PermissionCode.INVENTORY_VIEW, PermissionCode.INVENTORY_ADJUST, PermissionCode.INVENTORY_TRANSFER, PermissionCode.INVENTORY_MANAGE,
                    PermissionCode.INVENTORY_ITEM_MANAGE, PermissionCode.INVENTORY_LOCATION_MANAGE,
                    PermissionCode.INVENTORY_RECIPE_VIEW, PermissionCode.INVENTORY_RECIPE_MANAGE,
                    PermissionCode.INVENTORY_COST_VIEW, PermissionCode.INVENTORY_STOCK_ADJUST,
                    PermissionCode.INVENTORY_STOCK_WASTE, PermissionCode.INVENTORY_STOCK_COUNT,
                    PermissionCode.INVENTORY_STOCK_COUNT_APPROVE, PermissionCode.INVENTORY_STOCK_TRANSFER,
                    PermissionCode.INVENTORY_STOCK_TRANSFER_APPROVE, PermissionCode.INVENTORY_STOCK_RECEIVE,
                    PermissionCode.INVENTORY_ALERT_MANAGE, PermissionCode.INVENTORY_REPORT_VIEW,
                    PermissionCode.PURCHASING_VIEW, PermissionCode.PURCHASING_MANAGE,
                    PermissionCode.REPORTS_VIEW, PermissionCode.REPORTS_CASH_VIEW, PermissionCode.REPORTS_PAYMENTS_VIEW,
                    PermissionCode.REPORTS_EXPORT,
                    PermissionCode.USERS_VIEW, PermissionCode.USERS_CREATE, PermissionCode.USERS_UPDATE, PermissionCode.USERS_MANAGE,
                    PermissionCode.ROLES_VIEW, PermissionCode.ROLES_ASSIGN,
                    PermissionCode.AUDIT_VIEW,
                    PermissionCode.DEVICES_MANAGE, PermissionCode.HARDWARE_MANAGE,
                    PermissionCode.SETTINGS_VIEW, PermissionCode.SYNC_VIEW
            )
    ),
    ASSISTANT_MANAGER(
            "Assistant Manager",
            "Shift supervision, overrides, discounts, and operational oversight",
            "BRANCH",
            new BigDecimal("25.00"),
            EnumSet.of(
                    PermissionCode.DASHBOARD_VIEW,
                    PermissionCode.ORDERS_VIEW, PermissionCode.ORDERS_CREATE, PermissionCode.ORDERS_EDIT,
                    PermissionCode.ORDERS_UPDATE, PermissionCode.ORDERS_CANCEL,
                    PermissionCode.ORDERS_TRANSFER, PermissionCode.ORDERS_MERGE, PermissionCode.ORDERS_SPLIT,
                    PermissionCode.ORDERS_DISCOUNT, PermissionCode.ORDERS_DISCOUNT_APPLY, PermissionCode.ORDERS_DISCOUNT_APPROVE,
                    PermissionCode.PAYMENTS_VIEW, PermissionCode.PAYMENTS_PROCESS, PermissionCode.PAYMENTS_CREATE,
                    PermissionCode.PAYMENTS_REFUND, PermissionCode.PAYMENTS_REVERSE,
                    PermissionCode.CASH_OPEN, PermissionCode.CASH_CLOSE, PermissionCode.CASH_SESSION_OPEN,
                    PermissionCode.CASH_SESSION_CLOSE, PermissionCode.CASH_MOVEMENT_CREATE,
                    PermissionCode.CASH_DRAWER_OPEN, PermissionCode.CASH_DRAWER_OPEN_WITHOUT_SALE,
                    PermissionCode.KITCHEN_VIEW, PermissionCode.KITCHEN_UPDATE,
                    PermissionCode.KITCHEN_TICKET_ACCEPT, PermissionCode.KITCHEN_TICKET_START,
                    PermissionCode.KITCHEN_TICKET_READY, PermissionCode.KITCHEN_TICKET_HOLD,
                    PermissionCode.KITCHEN_TICKET_REFIRE, PermissionCode.KITCHEN_TICKET_SERVE,
                    PermissionCode.KITCHEN_EXPO_VIEW,
                    PermissionCode.TABLES_VIEW, PermissionCode.TABLES_MANAGE,
                    PermissionCode.MENU_VIEW, PermissionCode.MENU_UPDATE,
                    PermissionCode.INVENTORY_VIEW, PermissionCode.INVENTORY_ADJUST,
                    PermissionCode.INVENTORY_RECIPE_VIEW, PermissionCode.INVENTORY_STOCK_WASTE,
                    PermissionCode.INVENTORY_STOCK_COUNT, PermissionCode.INVENTORY_STOCK_COUNT_APPROVE,
                    PermissionCode.INVENTORY_STOCK_TRANSFER_APPROVE, PermissionCode.INVENTORY_ALERT_MANAGE,
                    PermissionCode.INVENTORY_REPORT_VIEW,
                    PermissionCode.REPORTS_VIEW, PermissionCode.REPORTS_CASH_VIEW, PermissionCode.REPORTS_PAYMENTS_VIEW,
                    PermissionCode.USERS_VIEW
            )
    ),
    CASHIER(
            "Cashier",
            "Front-counter order entry, payments, and personal cash shift handling",
            "BRANCH",
            new BigDecimal("10.00"),
            EnumSet.of(
                    PermissionCode.DASHBOARD_VIEW,
                    PermissionCode.ORDERS_VIEW, PermissionCode.ORDERS_CREATE, PermissionCode.ORDERS_EDIT,
                    PermissionCode.ORDERS_SPLIT, PermissionCode.ORDERS_DISCOUNT_APPLY,
                    PermissionCode.PAYMENTS_VIEW, PermissionCode.PAYMENTS_PROCESS, PermissionCode.PAYMENTS_CREATE,
                    PermissionCode.CASH_OPEN, PermissionCode.CASH_CLOSE, PermissionCode.CASH_SESSION_OPEN,
                    PermissionCode.CASH_SESSION_CLOSE, PermissionCode.CASH_MOVEMENT_CREATE, PermissionCode.CASH_DRAWER_OPEN,
                    PermissionCode.REPORTS_CASH_VIEW, PermissionCode.REPORTS_PAYMENTS_VIEW,
                    PermissionCode.TABLES_VIEW, PermissionCode.MENU_VIEW
            )
    ),
    WAITER(
            "Waiter",
            "Table service, order entry, and floor plan management",
            "BRANCH",
            new BigDecimal("5.00"),
            EnumSet.of(
                    PermissionCode.ORDERS_VIEW, PermissionCode.ORDERS_CREATE, PermissionCode.ORDERS_EDIT,
                    PermissionCode.ORDERS_TRANSFER, PermissionCode.ORDERS_SPLIT,
                    PermissionCode.TABLES_VIEW, PermissionCode.TABLES_MANAGE,
                    PermissionCode.MENU_VIEW, PermissionCode.KITCHEN_VIEW,
                    PermissionCode.KITCHEN_EXPO_VIEW, PermissionCode.KITCHEN_TICKET_SERVE
            )
    ),
    KITCHEN_STAFF(
            "Kitchen Staff",
            "Kitchen display ticket viewing and prep status updates",
            "BRANCH",
            BigDecimal.ZERO,
            EnumSet.of(
                    PermissionCode.KITCHEN_VIEW, PermissionCode.KITCHEN_UPDATE,
                    PermissionCode.KITCHEN_TICKET_ACCEPT, PermissionCode.KITCHEN_TICKET_START,
                    PermissionCode.KITCHEN_TICKET_READY, PermissionCode.KITCHEN_TICKET_HOLD,
                    PermissionCode.MENU_VIEW
            )
    ),
    CHEF(
            "Chef",
            "Kitchen station management, recipes, and prep coordination",
            "BRANCH",
            BigDecimal.ZERO,
            EnumSet.of(
                    PermissionCode.KITCHEN_VIEW, PermissionCode.KITCHEN_UPDATE, PermissionCode.KITCHEN_MANAGE,
                    PermissionCode.KITCHEN_STATION_MANAGE, PermissionCode.KITCHEN_ROUTING_MANAGE,
                    PermissionCode.KITCHEN_TICKET_ACCEPT, PermissionCode.KITCHEN_TICKET_START,
                    PermissionCode.KITCHEN_TICKET_READY, PermissionCode.KITCHEN_TICKET_CANCEL,
                    PermissionCode.KITCHEN_TICKET_HOLD, PermissionCode.KITCHEN_TICKET_REFIRE,
                    PermissionCode.KITCHEN_TICKET_SERVE, PermissionCode.KITCHEN_PRINTER_MANAGE,
                    PermissionCode.KITCHEN_EXPO_VIEW,
                    PermissionCode.MENU_VIEW, PermissionCode.INVENTORY_VIEW,
                    PermissionCode.INVENTORY_RECIPE_VIEW, PermissionCode.INVENTORY_RECIPE_MANAGE,
                    PermissionCode.INVENTORY_STOCK_WASTE
            )
    ),
    INVENTORY_MANAGER(
            "Inventory Manager",
            "Stock counts, adjustments, transfers, suppliers, and purchasing",
            "BRANCH",
            BigDecimal.ZERO,
            EnumSet.of(
                    PermissionCode.DASHBOARD_VIEW,
                    PermissionCode.INVENTORY_VIEW, PermissionCode.INVENTORY_ADJUST,
                    PermissionCode.INVENTORY_TRANSFER, PermissionCode.INVENTORY_MANAGE,
                    PermissionCode.INVENTORY_ITEM_MANAGE, PermissionCode.INVENTORY_LOCATION_MANAGE,
                    PermissionCode.INVENTORY_RECIPE_VIEW, PermissionCode.INVENTORY_RECIPE_MANAGE,
                    PermissionCode.INVENTORY_COST_VIEW, PermissionCode.INVENTORY_STOCK_ADJUST,
                    PermissionCode.INVENTORY_STOCK_WASTE, PermissionCode.INVENTORY_STOCK_COUNT,
                    PermissionCode.INVENTORY_STOCK_COUNT_APPROVE, PermissionCode.INVENTORY_STOCK_TRANSFER,
                    PermissionCode.INVENTORY_STOCK_TRANSFER_APPROVE, PermissionCode.INVENTORY_STOCK_RECEIVE,
                    PermissionCode.INVENTORY_ALERT_MANAGE, PermissionCode.INVENTORY_REPORT_VIEW,
                    PermissionCode.PURCHASING_VIEW, PermissionCode.PURCHASING_MANAGE,
                    PermissionCode.MENU_VIEW, PermissionCode.REPORTS_VIEW
            )
    ),
    ACCOUNTANT(
            "Accountant",
            "Financial reporting, exports, cash reconciliation review, and purchasing audit",
            "TENANT",
            BigDecimal.ZERO,
            EnumSet.of(
                    PermissionCode.DASHBOARD_VIEW,
                    PermissionCode.ORDERS_VIEW, PermissionCode.PAYMENTS_VIEW,
                    PermissionCode.CASH_SESSION_RECONCILE,
                    PermissionCode.INVENTORY_VIEW, PermissionCode.INVENTORY_COST_VIEW, PermissionCode.INVENTORY_REPORT_VIEW,
                    PermissionCode.PURCHASING_VIEW,
                    PermissionCode.REPORTS_VIEW, PermissionCode.REPORTS_EXPORT,
                    PermissionCode.AUDIT_VIEW
            )
    ),
    AUDITOR(
            "Auditor",
            "Read-only compliance review across orders, payments, inventory, and audit logs",
            "TENANT",
            BigDecimal.ZERO,
            EnumSet.of(
                    PermissionCode.DASHBOARD_VIEW,
                    PermissionCode.ORDERS_VIEW, PermissionCode.PAYMENTS_VIEW,
                    PermissionCode.MENU_VIEW, PermissionCode.INVENTORY_VIEW,
                    PermissionCode.INVENTORY_RECIPE_VIEW, PermissionCode.INVENTORY_COST_VIEW, PermissionCode.INVENTORY_REPORT_VIEW,
                    PermissionCode.PURCHASING_VIEW, PermissionCode.REPORTS_VIEW,
                    PermissionCode.USERS_VIEW, PermissionCode.ROLES_VIEW,
                    PermissionCode.AUDIT_VIEW, PermissionCode.SETTINGS_VIEW, PermissionCode.SYNC_VIEW
            )
    );

    private final String displayName;
    private final String description;
    private final String scopeType;
    private final BigDecimal maxDiscountPct;
    private final Set<PermissionCode> defaultPermissions;

    DefaultRoleTemplate(
            String displayName,
            String description,
            String scopeType,
            BigDecimal maxDiscountPct,
            Set<PermissionCode> defaultPermissions
    ) {
        this.displayName = displayName;
        this.description = description;
        this.scopeType = scopeType;
        this.maxDiscountPct = maxDiscountPct;
        this.defaultPermissions = Set.copyOf(defaultPermissions);
    }

    public String code() {
        return name();
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String scopeType() {
        return scopeType;
    }

    public BigDecimal maxDiscountPct() {
        return maxDiscountPct;
    }

    public Set<PermissionCode> defaultPermissions() {
        return defaultPermissions;
    }
}
