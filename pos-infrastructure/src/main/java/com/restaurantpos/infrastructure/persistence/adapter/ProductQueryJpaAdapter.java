package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.menu.ProductCatalogView;
import com.restaurantpos.domain.port.ProductQueryPort;
import com.restaurantpos.infrastructure.persistence.repository.ProductJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class ProductQueryJpaAdapter implements ProductQueryPort {

    private final ProductJpaRepository productRepository;

    public ProductQueryJpaAdapter(ProductJpaRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCatalogView> findBranchCatalog(TenantId tenantId, BranchId branchId, int page, int size) {
        return productRepository.findBranchCatalog(tenantId.value(), branchId.value(), PageRequest.of(page, size))
                .getContent();
    }
}
