package com.restaurantpos.infrastructure.persistence.adapter;

import com.restaurantpos.domain.port.UserRepositoryPort;
import com.restaurantpos.domain.security.UserAccount;
import com.restaurantpos.domain.security.UserRoleAssignment;
import com.restaurantpos.infrastructure.persistence.DatabaseExceptionTranslator;
import com.restaurantpos.infrastructure.persistence.entity.RoleEntity;
import com.restaurantpos.infrastructure.persistence.entity.UserBranchAccessEntity;
import com.restaurantpos.infrastructure.persistence.entity.UserEntity;
import com.restaurantpos.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.restaurantpos.infrastructure.persistence.repository.RoleJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.UserBranchAccessJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.UserJpaRepository;
import com.restaurantpos.infrastructure.persistence.repository.UserRoleAssignmentJpaRepository;
import com.restaurantpos.shared.model.BranchId;
import com.restaurantpos.shared.model.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "pos.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class UserJpaAdapter implements UserRepositoryPort {

    private final UserJpaRepository userRepository;
    private final UserBranchAccessJpaRepository branchAccessRepository;
    private final UserRoleAssignmentJpaRepository roleAssignmentRepository;
    private final RoleJpaRepository roleRepository;
    private final DatabaseExceptionTranslator translator;

    public UserJpaAdapter(
            UserJpaRepository userRepository,
            UserBranchAccessJpaRepository branchAccessRepository,
            UserRoleAssignmentJpaRepository roleAssignmentRepository,
            RoleJpaRepository roleRepository,
            DatabaseExceptionTranslator translator
    ) {
        this.userRepository = userRepository;
        this.branchAccessRepository = branchAccessRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.roleRepository = roleRepository;
        this.translator = translator;
    }

    @Override
    @Transactional
    public UserAccount save(UserAccount user) {
        UserEntity entity = userRepository.findByIdAndTenantId(user.id(), user.tenantId().value())
                .map(existing -> {
                    existing.setFullName(user.fullName());
                    existing.setEmail(user.email());
                    existing.setPasswordHash(user.passwordHash());
                    existing.setActive(user.active());
                    existing.setFailedLoginCount(user.failedLoginCount());
                    existing.setLockedUntil(user.lockedUntil());
                    existing.setLastLoginAt(user.lastLoginAt());
                    return existing;
                })
                .orElseGet(() -> {
                    UserEntity created = new UserEntity(
                            user.id(),
                            user.tenantId().value(),
                            user.username(),
                            user.fullName(),
                            user.email(),
                            user.passwordHash()
                    );
                    created.setActive(user.active());
                    created.setFailedLoginCount(user.failedLoginCount());
                    created.setLockedUntil(user.lockedUntil());
                    created.setLastLoginAt(user.lastLoginAt());
                    return created;
                });

        UserEntity saved = translator.execute("save user", () -> userRepository.saveAndFlush(entity));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByIdAndTenant(UUID userId, TenantId tenantId) {
        return userRepository.findByIdAndTenantId(userId, tenantId.value()).map(UserJpaAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByTenantAndUsername(TenantId tenantId, String username) {
        return userRepository.findByTenantIdAndUsername(tenantId.value(), username).map(UserJpaAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccount> findByTenant(TenantId tenantId, int page, int size) {
        return userRepository.findByTenantIdOrderByUsernameAsc(tenantId.value(), PageRequest.of(page, size))
                .stream()
                .map(UserJpaAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<BranchId> findAllowedBranches(TenantId tenantId, UUID userId) {
        return branchAccessRepository.findByIdUserIdAndTenantId(userId, tenantId.value()).stream()
                .map(e -> BranchId.of(e.getId().getBranchId()))
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void replaceBranchAccess(TenantId tenantId, UUID userId, Set<BranchId> branchIds) {
        branchAccessRepository.deleteByIdUserIdAndTenantId(userId, tenantId.value());
        branchAccessRepository.flush();
        for (BranchId branchId : branchIds) {
            branchAccessRepository.save(new UserBranchAccessEntity(userId, branchId.value(), tenantId.value()));
        }
        branchAccessRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserRoleAssignment> findRoleAssignments(TenantId tenantId, UUID userId) {
        List<UserRoleAssignmentEntity> entities = roleAssignmentRepository.findByTenantIdAndUserId(tenantId.value(), userId);
        Map<UUID, RoleEntity> rolesById = new HashMap<>();
        return entities.stream()
                .map(entity -> {
                    RoleEntity role = rolesById.computeIfAbsent(
                            entity.getRoleId(),
                            id -> roleRepository.findByIdAndTenantId(id, tenantId.value()).orElse(null)
                    );
                    return new UserRoleAssignment(
                            entity.getId(),
                            TenantId.of(entity.getTenantId()),
                            entity.getUserId(),
                            entity.getRoleId(),
                            role != null ? role.getCode() : "UNKNOWN",
                            role != null ? role.getName() : "Unknown",
                            entity.getBranchId()
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public void replaceRoleAssignments(TenantId tenantId, UUID userId, List<UserRoleAssignment> assignments) {
        roleAssignmentRepository.deleteByTenantIdAndUserId(tenantId.value(), userId);
        roleAssignmentRepository.flush();
        for (UserRoleAssignment assignment : assignments) {
            roleAssignmentRepository.save(new UserRoleAssignmentEntity(
                    assignment.id(),
                    tenantId.value(),
                    userId,
                    assignment.roleId(),
                    assignment.branchId()
            ));
        }
        roleAssignmentRepository.flush();
    }

    private static UserAccount toDomain(UserEntity entity) {
        return new UserAccount(
                entity.getId(),
                TenantId.of(entity.getTenantId()),
                entity.getUsername(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.isActive(),
                entity.getFailedLoginCount(),
                entity.getLockedUntil(),
                entity.getLastLoginAt(),
                entity.getVersion()
        );
    }
}
