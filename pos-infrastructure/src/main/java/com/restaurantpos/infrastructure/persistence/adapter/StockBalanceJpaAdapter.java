package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.inventory.StockBalanceView;
import com.restaurantpos.domain.port.StockBalanceRepositoryPort;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.StockBalanceEntity;
import com.restaurantpos.infrastructure.persistence.repository.StockBalanceJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class StockBalanceJpaAdapter implements StockBalanceRepositoryPort {

    private final StockBalanceJpaRepository repository;
    private final DatabaseExceptionTranslator translator;

    public StockBalanceJpaAdapter(StockBalanceJpaRepository repository, DatabaseExceptionTranslator translator) {
        this.repository = repository;
        this.translator = translator;
    }

    @Override
    @Transactional
    public StockBalanceView save(StockBalanceView balance) {
        return translator.execute("save stock balance", () -> {
            Optional<StockBalanceEntity> existingOpt = repository.findByTenantIdAndBranchIdAndStockLocationIdAndIngredientId(
                    balance.tenantId().value(),
                    balance.branchId().value(),
                    balance.stockLocationId(),
                    balance.ingredientId()
            );

            StockBalanceEntity entity;
            if (existingOpt.isPresent()) {
                entity = existingOpt.get();
                if (entity.getVersion() != balance.version()) {
                    throw new OptimisticLockingFailureException(
                            "StockBalance " + entity.getId() + " version mismatch: expected " + balance.version()
                                    + " but was " + entity.getVersion()
                    );
                }
                entity.setQuantityOnHand(balance.quantityOnHand());
                entity.setLastMovementAt(Instant.now());
            } else {
                entity = new StockBalanceEntity(
                        balance.id(),
                        balance.tenantId().value(),
                        balance.branchId().value(),
                        balance.stockLocationId(),
                        balance.ingredientId(),
                        balance.quantityOnHand()
                );
            }
            StockBalanceEntity saved = repository.saveAndFlush(entity);
            return toDomain(saved);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockBalanceView> findScoped(
            TenantId tenantId,
            BranchId branchId,
            UUID stockLocationId,
            UUID ingredientId
    ) {
        return repository.findByTenantIdAndBranchIdAndStockLocationIdAndIngredientId(
                tenantId.value(),
                branchId.value(),
                stockLocationId,
                ingredientId
        ).map(StockBalanceJpaAdapter::toDomain);
    }

    private static StockBalanceView toDomain(StockBalanceEntity entity) {
        return new StockBalanceView(
                entity.getId(),
                TenantId.of(entity.getTenantId()),
                BranchId.of(entity.getBranchId()),
                entity.getStockLocationId(),
                entity.getIngredientId(),
                entity.getQuantityOnHand(),
                entity.getVersion()
        );
    }
}
