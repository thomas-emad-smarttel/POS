package com.restaurantpos.infrastructure.persistence.repository;

import com.restaurantpos.infrastructure.persistence.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RolePermissionJpaRepository extends JpaRepository<RolePermissionEntity, RolePermissionEntity.RolePermissionId> {

    @Query("SELECT p.code FROM RolePermissionEntity rp JOIN PermissionEntity p ON p.id = rp.id.permissionId WHERE rp.id.roleId = :roleId")
    List<String> findPermissionCodesByRoleId(@Param("roleId") UUID roleId);

    void deleteByIdRoleId(UUID roleId);
}
