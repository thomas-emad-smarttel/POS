package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.TableRepositoryPort;
import com.restaurantpos.domain.table.DiningArea;
import com.restaurantpos.domain.table.DiningTable;
import com.restaurantpos.domain.table.TableStatus;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.DiningAreaEntity;
import com.restaurantpos.infrastructure.persistence.entity.DiningTableEntity;
import com.restaurantpos.infrastructure.persistence.repository.DiningAreaJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.DiningTableJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class TableJpaAdapter implements TableRepositoryPort {

    private final DiningAreaJpaRepository areaRepo;
    private final DiningTableJpaRepository tableRepo;
    private final DatabaseExceptionTranslator translator;

    public TableJpaAdapter(
            DiningAreaJpaRepository areaRepo,
            DiningTableJpaRepository tableRepo,
            DatabaseExceptionTranslator translator
    ) {
        this.areaRepo = areaRepo;
        this.tableRepo = tableRepo;
        this.translator = translator;
    }

    @Override
    @Transactional
    public DiningArea saveArea(DiningArea area) {
        DiningAreaEntity entity = areaRepo.findByIdAndTenantIdAndBranchId(area.id(), area.tenantId().value(), area.branchId().value())
                .map(existing -> {
                    existing.setName(area.name());
                    existing.setDisplayOrder(area.displayOrder());
                    existing.setActive(area.active());
                    return existing;
                })
                .orElseGet(() -> new DiningAreaEntity(
                        area.id(), area.tenantId().value(), area.branchId().value(),
                        area.code(), area.name(), area.displayOrder(), area.active()
                ));
        DiningAreaEntity saved = translator.execute("save dining area", () -> areaRepo.saveAndFlush(entity));
        return toAreaDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DiningArea> findAreaScoped(UUID areaId, TenantId tenantId, BranchId branchId) {
        return areaRepo.findByIdAndTenantIdAndBranchId(areaId, tenantId.value(), branchId.value())
                .map(TableJpaAdapter::toAreaDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiningArea> findAreasByBranch(TenantId tenantId, BranchId branchId) {
        return areaRepo.findByTenantIdAndBranchIdOrderByDisplayOrderAscNameAsc(tenantId.value(), branchId.value())
                .stream()
                .map(TableJpaAdapter::toAreaDomain)
                .toList();
    }

    @Override
    @Transactional
    public DiningTable saveTable(DiningTable table) {
        return translator.execute("save dining table", () -> {
            Optional<DiningTableEntity> existingOpt = tableRepo.findByIdAndTenantIdAndBranchId(
                    table.id(), table.tenantId().value(), table.branchId().value());
            DiningTableEntity entity;
            if (existingOpt.isPresent()) {
                entity = existingOpt.get();
                if (entity.getVersion() != table.version()) {
                    throw new OptimisticLockingFailureException("Table " + table.tableCode() + " version conflict");
                }
                entity.setDiningAreaId(table.diningAreaId());
                entity.setDisplayName(table.displayName());
                entity.setCapacity(table.capacity());
                entity.setSortOrder(table.sortOrder());
                entity.setStatus(table.status().name());
                entity.setActiveOrderId(table.activeOrderId());
                entity.setActive(table.active());
            } else {
                entity = new DiningTableEntity(
                        table.id(), table.tenantId().value(), table.branchId().value(), table.diningAreaId(),
                        table.tableCode(), table.displayName(), table.capacity(), table.sortOrder(),
                        table.status().name(), table.activeOrderId(), table.active()
                );
            }
            DiningTableEntity saved = tableRepo.saveAndFlush(entity);
            String areaName = areaRepo.findByIdAndTenantIdAndBranchId(
                    saved.getDiningAreaId(), saved.getTenantId(), saved.getBranchId())
                    .map(DiningAreaEntity::getName).orElse("");
            return toTableDomain(saved, areaName);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DiningTable> findTableScoped(UUID tableId, TenantId tenantId, BranchId branchId) {
        return tableRepo.findByIdAndTenantIdAndBranchId(tableId, tenantId.value(), branchId.value())
                .map(t -> {
                    String areaName = areaRepo.findByIdAndTenantIdAndBranchId(t.getDiningAreaId(), tenantId.value(), branchId.value())
                            .map(DiningAreaEntity::getName).orElse("");
                    return toTableDomain(t, areaName);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiningTable> findTablesByBranch(TenantId tenantId, BranchId branchId) {
        Map<UUID, String> areaNames = areaRepo.findByTenantIdAndBranchIdOrderByDisplayOrderAscNameAsc(
                        tenantId.value(), branchId.value())
                .stream()
                .collect(Collectors.toMap(DiningAreaEntity::getId, DiningAreaEntity::getName));
        return tableRepo.findByTenantIdAndBranchIdOrderBySortOrderAscTableCodeAsc(tenantId.value(), branchId.value())
                .stream()
                .map(t -> toTableDomain(t, areaNames.getOrDefault(t.getDiningAreaId(), "")))
                .toList();
    }

    private static DiningArea toAreaDomain(DiningAreaEntity e) {
        return new DiningArea(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getCode(), e.getName(), e.getDisplayOrder(), e.isActive(), e.getVersion()
        );
    }

    private static DiningTable toTableDomain(DiningTableEntity e, String areaName) {
        return new DiningTable(
                e.getId(), TenantId.of(e.getTenantId()), BranchId.of(e.getBranchId()),
                e.getDiningAreaId(), areaName, e.getTableCode(), e.getDisplayName(),
                e.getCapacity(), e.getSortOrder(), TableStatus.valueOf(e.getStatus()),
                e.getActiveOrderId(), e.isActive(), e.getVersion()
        );
    }
}
