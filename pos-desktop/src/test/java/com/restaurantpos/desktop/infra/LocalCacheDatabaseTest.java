package com.restaurantpos.desktop.infra;

import com.restaurantpos.shared.dto.pos.CategoryDto;
import com.restaurantpos.shared.dto.pos.ProductDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalCacheDatabaseTest {

    @Test
    void initializesWalSqliteDatabaseAndPersistsMetadata(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("test-terminal.db");
        LocalCacheDatabase db = new LocalCacheDatabase(dbFile);
        db.initialize();
        db.putMetadata("terminal_code", "POS-01");

        assertEquals("POS-01", db.getMetadata("terminal_code").orElseThrow());
    }

    @Test
    void cachesAndReloadsBranchCategoriesAndProductsForOfflineBrowsing(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("menu-cache.db");
        LocalCacheDatabase db = new LocalCacheDatabase(dbFile);
        db.initialize();

        UUID branchId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        CategoryDto category = new CategoryDto(catId, null, "BURGERS", "Burgers", 1, "#2563EB", true, true, 0L);
        ProductDto product = new ProductDto(
                UUID.randomUUID(),
                catId,
                "Burgers",
                UUID.randomUUID(),
                "BRG-001",
                "1234567890",
                "Classic Burger",
                "Flame grilled beef patty",
                new BigDecimal("12.00"),
                new BigDecimal("11.00"),
                "STANDARD",
                "FOOD",
                null,
                1,
                true,
                true,
                List.of(),
                List.of(),
                1L
        );

        db.replaceBranchCategories(branchId, List.of(category));
        db.replaceBranchProducts(branchId, List.of(product));

        List<CategoryDto> loadedCats = db.readBranchCategories(branchId);
        List<ProductDto> loadedProducts = db.readBranchProducts(branchId);

        assertEquals(1, loadedCats.size());
        assertEquals("Burgers", loadedCats.getFirst().name());
        assertEquals(1, loadedProducts.size());
        assertEquals("Classic Burger", loadedProducts.getFirst().name());
        assertEquals(0, new BigDecimal("11.00").compareTo(loadedProducts.getFirst().effectivePrice()));
    }
}
