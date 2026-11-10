package com.restaurantpos.domain.menu;

import com.restaurantpos.domain.exception.DomainException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validates customer modifier selections against a product's attached modifier groups
 * (requiredness, minSelections, maxSelections, and active status).
 */
public final class ModifierSelectionValidator {

    public record Selection(UUID modifierId, int quantity) {
    }

    private ModifierSelectionValidator() {
    }

    public static List<SelectedResolvedModifier> validateAndResolve(
            List<ModifierGroup> productGroups,
            List<Selection> requestedSelections
    ) {
        Map<UUID, Modifier> allowedModifiersById = new HashMap<>();
        Map<UUID, ModifierGroup> groupByModifierId = new HashMap<>();
        for (ModifierGroup group : productGroups) {
            if (!group.active()) {
                continue;
            }
            for (Modifier mod : group.modifiers()) {
                if (mod.active()) {
                    allowedModifiersById.put(mod.id(), mod);
                    groupByModifierId.put(mod.id(), group);
                }
            }
        }

        Map<UUID, Integer> countsByGroup = new HashMap<>();
        List<SelectedResolvedModifier> resolved = new java.util.ArrayList<>();

        if (requestedSelections != null) {
            for (Selection sel : requestedSelections) {
                if (sel.quantity() <= 0) {
                    throw new DomainException("INVALID_MODIFIER_QTY", "Modifier quantity must be positive");
                }
                Modifier mod = allowedModifiersById.get(sel.modifierId());
                if (mod == null) {
                    throw new DomainException(
                            "INVALID_MODIFIER_SELECTION",
                            "Modifier " + sel.modifierId() + " is not valid or active for this product"
                    );
                }
                ModifierGroup group = groupByModifierId.get(sel.modifierId());
                countsByGroup.merge(group.id(), 1, Integer::sum);
                resolved.add(new SelectedResolvedModifier(mod, sel.quantity()));
            }
        }

        for (ModifierGroup group : productGroups) {
            if (!group.active()) {
                continue;
            }
            int selectedCount = countsByGroup.getOrDefault(group.id(), 0);
            int effectiveMin = Math.max(group.required() ? 1 : 0, group.minSelections());
            if (selectedCount < effectiveMin) {
                throw new DomainException(
                        "MODIFIER_MIN_NOT_MET",
                        "Modifier group '" + group.name() + "' requires at least " + effectiveMin + " selection(s)"
                );
            }
            if (selectedCount > group.maxSelections()) {
                throw new DomainException(
                        "MODIFIER_MAX_EXCEEDED",
                        "Modifier group '" + group.name() + "' allows at most " + group.maxSelections() + " selection(s)"
                );
            }
        }

        return List.copyOf(resolved);
    }

    public record SelectedResolvedModifier(Modifier modifier, int quantity) {
    }
}
