package com.restaurantpos.shared.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Centralized, action-based permission catalog following {@code <module>.<action>} naming.
 */
public enum PermissionCode {
    DASHBOARD_VIEW("dashboard.view", PermissionModule.DASHBOARD, "View branch dashboard and operational metrics", false),

    ORDERS_VIEW("orders.view", PermissionModule.ORDERS, "View orders and order history", false),
    ORDERS_CREATE("orders.create", PermissionModule.ORDERS, "Create new customer orders", false),
    ORDERS_EDIT("orders.edit", PermissionModule.ORDERS, "Edit open order items and notes", false),
    ORDERS_UPDATE("orders.update", PermissionModule.ORDERS, "Modify open order items and notes", false),
    ORDERS_CANCEL("orders.cancel", PermissionModule.ORDERS, "Cancel open or paid orders", true),
    ORDERS_TRANSFER("orders.transfer", PermissionModule.ORDERS, "Transfer orders between dining tables", false),
    ORDERS_MERGE("orders.merge", PermissionModule.ORDERS, "Merge two open orders into one", true),
    ORDERS_SPLIT("orders.split", PermissionModule.ORDERS, "Split items from an open order into a new order", false),
    ORDERS_DISCOUNT("orders.discount", PermissionModule.ORDERS, "Apply order discounts within role limit", false),
    ORDERS_DISCOUNT_APPLY("orders.discount.apply", PermissionModule.ORDERS, "Apply standard discounts within limit", false),
    ORDERS_DISCOUNT_APPROVE("orders.discount.approve", PermissionModule.ORDERS, "Approve manager-level discounts", true),

    PAYMENTS_VIEW("payments.view", PermissionModule.PAYMENTS, "View payment transactions", false),
    PAYMENTS_PROCESS("payments.process", PermissionModule.PAYMENTS, "Process customer payments", false),
    PAYMENTS_CREATE("payments.create", PermissionModule.PAYMENTS, "Process customer payments", false),
    PAYMENTS_REFUND("payments.refund", PermissionModule.PAYMENTS, "Issue refunds for completed payments", true),
    PAYMENTS_REVERSE("payments.reverse", PermissionModule.PAYMENTS, "Reverse unsettled or reversible card payments", true),
    PAYMENTS_VOID("payments.void", PermissionModule.PAYMENTS, "Void unsettled payment authorizations", true),

    CASH_OPEN("cash.open", PermissionModule.CASH, "Open a cashier shift session", false),
    CASH_CLOSE("cash.close", PermissionModule.CASH, "Close a cashier shift session", false),
    CASH_SESSION_OPEN("cash.session.open", PermissionModule.CASH, "Open a cashier shift session", false),
    CASH_SESSION_CLOSE("cash.session.close", PermissionModule.CASH, "Close a cashier shift session", false),
    CASH_SESSION_RECONCILE("cash.session.reconcile", PermissionModule.CASH, "Reconcile and adjust cash drawer totals", true),
    CASH_SESSION_REOPEN("cash.session.reopen", PermissionModule.CASH, "Reopen a closed cash session with audit trail", true),
    CASH_MOVEMENT_CREATE("cash.movement.create", PermissionModule.CASH, "Record paid-in, paid-out, withdrawal, or deposit cash movements", false),
    CASH_DRAWER_OPEN("cash.drawer.open", PermissionModule.CASH, "Trigger cash drawer kick", true),
    CASH_DRAWER_OPEN_WITHOUT_SALE("cash.drawer.open_without_sale", PermissionModule.CASH, "Open cash drawer manually outside of a sale", true),

    KITCHEN_VIEW("kitchen.view", PermissionModule.KITCHEN, "View Kitchen Display System tickets", false),
    KITCHEN_UPDATE("kitchen.update", PermissionModule.KITCHEN, "Update kitchen ticket preparation status", false),
    KITCHEN_MANAGE("kitchen.manage", PermissionModule.KITCHEN, "Manage kitchen stations and ticket routing", false),
    KITCHEN_STATION_MANAGE("kitchen.station.manage", PermissionModule.KITCHEN, "Create, edit, and deactivate branch kitchen stations", false),
    KITCHEN_ROUTING_MANAGE("kitchen.routing.manage", PermissionModule.KITCHEN, "Configure kitchen routing rules and prep thresholds", false),
    KITCHEN_TICKET_ACCEPT("kitchen.ticket.accept", PermissionModule.KITCHEN, "Accept dispatched kitchen tickets", false),
    KITCHEN_TICKET_START("kitchen.ticket.start", PermissionModule.KITCHEN, "Start preparation on kitchen tickets", false),
    KITCHEN_TICKET_READY("kitchen.ticket.ready", PermissionModule.KITCHEN, "Mark kitchen items or tickets ready", false),
    KITCHEN_TICKET_CANCEL("kitchen.ticket.cancel", PermissionModule.KITCHEN, "Cancel kitchen tickets or items with reason", true),
    KITCHEN_TICKET_HOLD("kitchen.ticket.hold", PermissionModule.KITCHEN, "Place kitchen tickets on hold or release them", false),
    KITCHEN_TICKET_REFIRE("kitchen.ticket.refire", PermissionModule.KITCHEN, "Re-fire kitchen tickets or items with audit record", true),
    KITCHEN_TICKET_SERVE("kitchen.ticket.serve", PermissionModule.KITCHEN, "Serve or bump ready kitchen tickets", false),
    KITCHEN_PRINTER_MANAGE("kitchen.printer.manage", PermissionModule.KITCHEN, "Configure station printers and retry print jobs", true),
    KITCHEN_EXPO_VIEW("kitchen.expo.view", PermissionModule.KITCHEN, "View Kitchen Expo order readiness screen", false),

    TABLES_VIEW("tables.view", PermissionModule.TABLES, "View floor plan and table statuses", false),
    TABLES_MANAGE("tables.manage", PermissionModule.TABLES, "Assign, merge, or configure tables", false),

    MENU_VIEW("menu.view", PermissionModule.MENU, "View menu categories, items, and modifiers", false),
    MENU_CREATE("menu.create", PermissionModule.MENU, "Create new menu items and categories", false),
    MENU_UPDATE("menu.update", PermissionModule.MENU, "Update menu items, pricing, and availability", false),
    MENU_DELETE("menu.delete", PermissionModule.MENU, "Archive or delete menu items", true),
    MENU_MANAGE("menu.manage", PermissionModule.MENU, "Full menu catalog and branch pricing management", false),

    INVENTORY_VIEW("inventory.view", PermissionModule.INVENTORY, "View stock balances and movements", false),
    INVENTORY_ADJUST("inventory.adjust", PermissionModule.INVENTORY, "Record manual stock adjustments", true),
    INVENTORY_TRANSFER("inventory.transfer", PermissionModule.INVENTORY, "Transfer stock across locations", false),
    INVENTORY_MANAGE("inventory.manage", PermissionModule.INVENTORY, "Manage ingredients, recipes, and stock locations", false),
    INVENTORY_ITEM_MANAGE("inventory.item.manage", PermissionModule.INVENTORY, "Create and edit inventory items and categories", false),
    INVENTORY_LOCATION_MANAGE("inventory.location.manage", PermissionModule.INVENTORY, "Create and edit branch storage locations", false),
    INVENTORY_RECIPE_VIEW("inventory.recipe.view", PermissionModule.INVENTORY, "View product recipes and sub-recipes", false),
    INVENTORY_RECIPE_MANAGE("inventory.recipe.manage", PermissionModule.INVENTORY, "Create, edit, and publish recipe versions", false),
    INVENTORY_COST_VIEW("inventory.cost.view", PermissionModule.INVENTORY, "View item costs, recipe costs, and valuation reports", true),
    INVENTORY_STOCK_ADJUST("inventory.stock.adjust", PermissionModule.INVENTORY, "Post manual inventory adjustments", true),
    INVENTORY_STOCK_WASTE("inventory.stock.waste", PermissionModule.INVENTORY, "Record inventory waste and spoilage", false),
    INVENTORY_STOCK_COUNT("inventory.stock.count", PermissionModule.INVENTORY, "Create and submit physical stock counts", false),
    INVENTORY_STOCK_COUNT_APPROVE("inventory.stock.count.approve", PermissionModule.INVENTORY, "Approve and post physical stock count variances", true),
    INVENTORY_STOCK_TRANSFER("inventory.stock.transfer", PermissionModule.INVENTORY, "Request and dispatch inter/intra-branch stock transfers", false),
    INVENTORY_STOCK_TRANSFER_APPROVE("inventory.stock.transfer.approve", PermissionModule.INVENTORY, "Approve branch stock transfer requests", true),
    INVENTORY_STOCK_RECEIVE("inventory.stock.receive", PermissionModule.INVENTORY, "Receive incoming branch stock transfers", false),
    INVENTORY_ALERT_MANAGE("inventory.alert.manage", PermissionModule.INVENTORY, "Acknowledge and resolve inventory alerts", false),
    INVENTORY_REPORT_VIEW("inventory.report.view", PermissionModule.INVENTORY, "View low-stock, waste, and inventory reports", false),

    PURCHASING_VIEW("purchasing.view", PermissionModule.PURCHASING, "View suppliers and purchase orders", false),
    PURCHASING_MANAGE("purchasing.manage", PermissionModule.PURCHASING, "Create, approve, and receive purchase orders", true),

    REPORTS_VIEW("reports.view", PermissionModule.REPORTS, "View operational and financial reports", false),
    REPORTS_CASH_VIEW("reports.cash.view", PermissionModule.REPORTS, "View cash session reports and drawer balances", false),
    REPORTS_PAYMENTS_VIEW("reports.payments.view", PermissionModule.REPORTS, "View payment ledger and reconciliation reports", false),
    REPORTS_EXPORT("reports.export", PermissionModule.REPORTS, "Export reports to CSV or PDF", true),

    USERS_VIEW("users.view", PermissionModule.USERS, "View employee accounts", false),
    USERS_CREATE("users.create", PermissionModule.USERS, "Create employee accounts", true),
    USERS_UPDATE("users.update", PermissionModule.USERS, "Edit employee details and branch access", true),
    USERS_DISABLE("users.disable", PermissionModule.USERS, "Activate or deactivate employee accounts", true),
    USERS_MANAGE("users.manage", PermissionModule.USERS, "Create, edit, activate/deactivate users and branch access", true),

    ROLES_VIEW("roles.view", PermissionModule.ROLES, "View RBAC roles and permission matrices", false),
    ROLES_CREATE("roles.create", PermissionModule.ROLES, "Create custom RBAC roles", true),
    ROLES_UPDATE("roles.update", PermissionModule.ROLES, "Modify role permissions", true),
    ROLES_MANAGE("roles.manage", PermissionModule.ROLES, "Create, edit, activate/deactivate roles and permissions", true),
    ROLES_ASSIGN("roles.assign", PermissionModule.ROLES, "Assign or remove roles for users", true),

    AUDIT_VIEW("audit.view", PermissionModule.AUDIT, "View security audit logs and active sessions", true),

    DEVICES_MANAGE("devices.manage", PermissionModule.DEVICES, "Configure printers, drawers, scanners, and scales", true),
    HARDWARE_MANAGE("hardware.manage", PermissionModule.DEVICES, "Manage POS hardware devices and drivers", true),
    BRANCHES_MANAGE("branches.manage", PermissionModule.BRANCHES, "Manage branch profiles and operating settings", true),

    SETTINGS_VIEW("settings.view", PermissionModule.SETTINGS, "View system and branch settings", false),
    SETTINGS_MANAGE("settings.manage", PermissionModule.SETTINGS, "Manage system, security, and tax settings", true),

    SYNC_VIEW("sync.view", PermissionModule.SYNC, "View synchronization status, outbox, and conflicts", false),
    SYNC_MANAGE("sync.manage", PermissionModule.SYNC, "Resolve sync conflicts and retry dead letters", true);

    private final String code;
    private final PermissionModule module;
    private final String description;
    private final boolean sensitive;

    PermissionCode(String code, PermissionModule module, String description, boolean sensitive) {
        this.code = code;
        this.module = module;
        this.description = description;
        this.sensitive = sensitive;
    }

    public String code() {
        return code;
    }

    public PermissionModule module() {
        return module;
    }

    public String description() {
        return description;
    }

    public boolean sensitive() {
        return sensitive;
    }

    public static Optional<PermissionCode> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst();
    }
}
