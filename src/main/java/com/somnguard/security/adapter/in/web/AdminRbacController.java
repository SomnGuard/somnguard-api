package com.somnguard.security.adapter.in.web;

import com.somnguard.platform.security.RequireFeature;
import com.somnguard.security.adapter.in.web.dto.AssignRoleFeatureRequest;
import com.somnguard.security.adapter.in.web.dto.AssignRoleToUserPathRequest;
import com.somnguard.security.adapter.in.web.dto.FeatureRequest;
import com.somnguard.security.adapter.in.web.dto.RoleRequest;
import com.somnguard.security.adapter.out.persistence.entity.FeatureEntity;
import com.somnguard.security.adapter.out.persistence.entity.ModuleEntity;
import com.somnguard.security.adapter.out.persistence.entity.RoleEntity;
import com.somnguard.security.adapter.out.persistence.entity.RoleFeatureEntity;
import com.somnguard.security.adapter.out.persistence.entity.UserRoleEntity;
import com.somnguard.security.adapter.out.persistence.repository.FeatureRepository;
import com.somnguard.security.adapter.out.persistence.repository.ModuleRepository;
import com.somnguard.security.adapter.out.persistence.repository.RoleFeatureRepository;
import com.somnguard.security.adapter.out.persistence.repository.RoleRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import com.somnguard.security.adapter.out.persistence.repository.UserRoleRepository;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequireFeature("role.write")
public class AdminRbacController {

    private final RoleRepository roleRepository;
    private final FeatureRepository featureRepository;
    private final ModuleRepository moduleRepository;
    private final RoleFeatureRepository roleFeatureRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;

    public AdminRbacController(RoleRepository roleRepository, FeatureRepository featureRepository,
            ModuleRepository moduleRepository, RoleFeatureRepository roleFeatureRepository,
            UserRoleRepository userRoleRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.featureRepository = featureRepository;
        this.moduleRepository = moduleRepository;
        this.roleFeatureRepository = roleFeatureRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
    }

    // AC-001 CRUD roles
    @GetMapping("/roles")
    @RequireFeature("role.read")
    public List<RoleEntity> listRoles() { return roleRepository.findAll(); }

    @PostMapping("/roles")
    public ResponseEntity<RoleEntity> createRole(@Valid @RequestBody RoleRequest req) {
        if (roleRepository.findByCodeAndIsActiveTrue(req.code()).isPresent()) throw new IllegalStateException("Role code exists");
        RoleEntity r = new RoleEntity();
        r.setId(UUID.randomUUID());
        r.setCode(req.code());
        r.setName(req.name());
        r.setDescription(req.description());
        r.setIsActive(true);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        return ResponseEntity.status(201).body(roleRepository.save(r));
    }

    @PutMapping("/roles/{id}")
    public RoleEntity updateRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest req) {
        RoleEntity r = roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));
        r.setCode(req.code());
        r.setName(req.name());
        r.setDescription(req.description());
        r.setUpdatedAt(OffsetDateTime.now());
        return roleRepository.save(r);
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        RoleEntity r = roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));
        r.setIsActive(false);
        r.setUpdatedAt(OffsetDateTime.now());
        roleRepository.save(r);
        return ResponseEntity.noContent().build();
    }

    // AC-001 CRUD features
    @GetMapping("/features")
    @RequireFeature("role.read")
    public List<FeatureEntity> listFeatures() { return featureRepository.findAll(); }

    // docs: GET /api/v1/modules + GET /api/v1/modules/{id}/features
    @GetMapping("/modules")
    @RequireFeature("role.read")
    public List<ModuleEntity> listModules() { return moduleRepository.findAll(); }

    @GetMapping("/modules/{id}/features")
    @RequireFeature("role.read")
    public List<FeatureEntity> listFeaturesByModule(@PathVariable UUID id) {
        moduleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Module not found"));
        return featureRepository.findByModuleId(id);
    }

    @PostMapping("/features")
    public ResponseEntity<FeatureEntity> createFeature(@Valid @RequestBody FeatureRequest req) {
        ModuleEntity mod = moduleRepository.findById(req.moduleId()).orElseThrow(() -> new IllegalArgumentException("Module not found"));
        FeatureEntity f = new FeatureEntity();
        f.setId(UUID.randomUUID());
        f.setModuleId(mod.getId());
        f.setCode(req.code());
        f.setName(req.name());
        f.setDescription(req.description());
        f.setCreatedAt(OffsetDateTime.now());
        f.setUpdatedAt(OffsetDateTime.now());
        return ResponseEntity.status(201).body(featureRepository.save(f));
    }

    @PutMapping("/features/{id}")
    public FeatureEntity updateFeature(@PathVariable UUID id, @Valid @RequestBody FeatureRequest req) {
        FeatureEntity f = featureRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Feature not found"));
        f.setCode(req.code());
        f.setName(req.name());
        f.setDescription(req.description());
        f.setUpdatedAt(OffsetDateTime.now());
        return featureRepository.save(f);
    }

    @DeleteMapping("/features/{id}")
    public ResponseEntity<Void> deleteFeature(@PathVariable UUID id) {
        featureRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // docs: POST /api/v1/users/{id}/roles + DELETE /api/v1/users/{id}/roles/{roleId}
    @PostMapping("/users/{id}/roles")
    public ResponseEntity<UserRoleEntity> assignRoleToUserByPath(@PathVariable UUID id, @Valid @RequestBody AssignRoleToUserPathRequest body) {
        UUID roleId = body.roleId();
        userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        roleRepository.findById(roleId).orElseThrow(() -> new IllegalArgumentException("Role not found"));
        UserRoleEntity ur = new UserRoleEntity();
        ur.setId(UUID.randomUUID());
        ur.setUserId(id);
        ur.setRoleId(roleId);
        ur.setAssignedAt(OffsetDateTime.now());
        ur.setCreatedAt(OffsetDateTime.now());
        ur.setUpdatedAt(OffsetDateTime.now());
        ur.setIsActive(true);
        ur.setVersion(1);
        return ResponseEntity.status(201).body(userRoleRepository.save(ur));
    }

    @DeleteMapping("/users/{id}/roles/{roleId}")
    public ResponseEntity<Void> removeRoleFromUser(@PathVariable UUID id, @PathVariable UUID roleId) {
        var list = userRoleRepository.findActiveByUserId(id, OffsetDateTime.now());
        var ur = list.stream().filter(x -> x.getRoleId().equals(roleId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("user_role not found"));
        ur.setDeletedAt(OffsetDateTime.now());
        ur.setIsActive(false);
        userRoleRepository.save(ur);
        return ResponseEntity.noContent().build();
    }

    // AC-002 role_feature
    @PostMapping("/role-features")
    public ResponseEntity<RoleFeatureEntity> assignRoleFeature(@Valid @RequestBody AssignRoleFeatureRequest req) {
        UUID roleId = req.roleId();
        UUID featureId = req.featureId();
        roleRepository.findById(roleId).orElseThrow(() -> new IllegalArgumentException("Role not found"));
        featureRepository.findById(featureId).orElseThrow(() -> new IllegalArgumentException("Feature not found"));
        if (roleFeatureRepository.existsByRoleIdAndFeatureIdAndDeletedAtIsNull(roleId, featureId)) throw new IllegalStateException("role_feature exists");
        RoleFeatureEntity rf = new RoleFeatureEntity();
        rf.setId(UUID.randomUUID());
        rf.setRoleId(roleId);
        rf.setFeatureId(featureId);
        rf.setCreatedAt(OffsetDateTime.now());
        rf.setUpdatedAt(OffsetDateTime.now());
        rf.setIsActive(true);
        rf.setVersion(1);
        return ResponseEntity.status(201).body(roleFeatureRepository.save(rf));
    }

    @DeleteMapping("/role-features/{id}")
    public ResponseEntity<Void> removeRoleFeature(@PathVariable UUID id) {
        RoleFeatureEntity rf = roleFeatureRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("role_feature not found"));
        rf.setDeletedAt(OffsetDateTime.now());
        rf.setIsActive(false);
        roleFeatureRepository.save(rf);
        return ResponseEntity.noContent().build();
    }
}
