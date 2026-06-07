package com.makerspace.backend;

import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for soft-delete on User.
 *
 * Uses H2 in-memory DB (application-test.properties).
 * Verifies @SQLDelete, @SQLRestriction, and native query behaviour.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        return userRepository.save(user);
    }

    // --- @SQLRestriction: active-only filtering ---

    @Test
    void findAll_excludesSoftDeletedUser() {
        createUser("active@test.com");
        User toDelete = createUser("deleted@test.com");

        userRepository.delete(toDelete);
        testEntityManager.flush();
        testEntityManager.clear();

        List<User> result = userRepository.findAll();
        assertThat(result).extracting(User::getEmail)
                .containsExactly("active@test.com")
                .doesNotContain("deleted@test.com");
    }

    @Test
    void findByEmail_returnsEmpty_forSoftDeletedUser() {
        User user = createUser("gone@test.com");

        userRepository.delete(user);
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(userRepository.findByEmail("gone@test.com")).isEmpty();
    }

    @Test
    void findById_returnsEmpty_forSoftDeletedUser() {
        User user = createUser("byid@test.com");
        Long id = user.getId();

        userRepository.delete(user);
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(userRepository.findById(id)).isEmpty();
    }

    // --- @SQLDelete: soft-delete preserves the row ---

    @Test
    void softDelete_preservesRow_withDeletedAtSet() {
        User user = createUser("preserved@test.com");
        Long id = user.getId();

        userRepository.delete(user);
        testEntityManager.flush();
        testEntityManager.clear();

        // Bypass @SQLRestriction with a native query to confirm row still exists
        EntityManager em = testEntityManager.getEntityManager();
        Long count = (Long) em
                .createNativeQuery("SELECT COUNT(*) FROM users WHERE id = :id AND deleted_at IS NOT NULL")
                .setParameter("id", id)
                .getSingleResult();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void softDelete_doesNotPhysicallyDeleteRow() {
        User user = createUser("nodrop@test.com");
        Long id = user.getId();

        userRepository.delete(user);
        testEntityManager.flush();
        testEntityManager.clear();

        EntityManager em = testEntityManager.getEntityManager();
        Long total = (Long) em
                .createNativeQuery("SELECT COUNT(*) FROM users WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();

        assertThat(total).isEqualTo(1L);
    }

    // --- findByIdIncludingDeleted ---

    @Test
    void findByIdIncludingDeleted_returnsSoftDeletedUser() {
        User user = createUser("findbyid@test.com");
        Long id = user.getId();

        userRepository.delete(user);
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(userRepository.findByIdIncludingDeleted(id)).isPresent();
        assertThat(userRepository.findByIdIncludingDeleted(id).get().getDeletedAt()).isNotNull();
    }

    @Test
    void findByIdIncludingDeleted_returnsEmpty_forNonexistentId() {
        assertThat(userRepository.findByIdIncludingDeleted(99999L)).isEmpty();
    }

    // --- Unique constraint: deleted user re-registration ---

    /**
     * Reproduces the bug in UserService.findOrCreate:
     * Because @SQLRestriction hides the deleted row, findByEmail returns empty,
     * so findOrCreate tries to INSERT a new row — violating the UNIQUE constraint on email.
     *
     * Fix options:
     *  1. Query for deleted users separately in findOrCreate and restore them.
     *  2. Remove the UNIQUE constraint and deduplicate by active status.
     */
    @Test
    void save_throwsConstraintViolation_whenEmailMatchesSoftDeletedRow() {
        User original = createUser("reregister@test.com");
        userRepository.delete(original);
        testEntityManager.flush();
        testEntityManager.clear();

        User duplicate = new User();
        duplicate.setEmail("reregister@test.com");
        duplicate.setFirstName("Same");
        duplicate.setLastName("Email");

        assertThatThrownBy(() -> {
            userRepository.save(duplicate);
            testEntityManager.flush();
        }).isInstanceOf(Exception.class); // DataIntegrityViolationException / ConstraintViolationException
    }
}