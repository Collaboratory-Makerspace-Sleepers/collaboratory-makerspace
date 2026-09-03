package com.makerspace.backend.repository;

import com.makerspace.backend.model.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppRoleRepository extends JpaRepository<AppRole, String> {

    /** True if any active (non-deleted) user holds this role. */
    @Query(value = """
        SELECT COUNT(*) > 0
        FROM user_roles ur
        JOIN users u ON u.id = ur.user_id
        WHERE ur.role = :roleCode AND u.deleted_at IS NULL
        """, nativeQuery = true)
    boolean isRoleInUse(@Param("roleCode") String roleCode);
}
