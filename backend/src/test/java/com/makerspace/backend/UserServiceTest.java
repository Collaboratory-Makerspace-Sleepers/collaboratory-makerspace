package com.makerspace.backend;

import com.makerspace.backend.config.security.OAuthProfile;
import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.services.UserService;
import com.makerspace.backend.services.UserStateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStateService userStateService;

    @InjectMocks
    private UserService userService;

    private User makeUser(String email) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(Role.MEMBER);
        return user;
    }

    private OAuthProfile makeProfile(String email, String firstName, String lastName) {
        return new OAuthProfile(email, firstName, lastName, "sub-123");
    }

    // --- findUser ---

    @Test
    void findUser_returnsExistingUser_whenEmailFound() {
        User existing = makeUser("existing@test.com");
        when(userRepository.findByEmail("existing@test.com")).thenReturn(Optional.of(existing));

        User result = userService.findUser("existing@test.com");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    // --- provision ---

    @Test
    void provision_savesUserWithCorrectFields() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.provision(makeProfile("new@test.com", "New", "Person"));

        assertThat(result.getEmail()).isEqualTo("new@test.com");
        assertThat(result.getFirstName()).isEqualTo("New");
        assertThat(result.getLastName()).isEqualTo("Person");
        assertThat(result.getRole()).isEqualTo(Role.MEMBER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void provision_assignsMemberRole_toNewUser() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.provision(makeProfile("member@test.com", "Member", "User"));

        assertThat(result.getRole()).isEqualTo(Role.MEMBER);
    }

    /**
     * provision() is intentionally INSERT-only. Callers (e.g. OAuth2SuccessHandler)
     * are responsible for calling resolve() first and only dispatching to provision()
     * on a NotFound result. Calling provision() for a deleted email would still hit
     * the UNIQUE constraint — that path is now guarded at the call site.
     */
    @Test
    void provision_attemptsToSaveNewUser_whenSoftDeletedEmailIsInvisible() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // No DB check — provision blindly inserts, which blows up at flush time in production
        User result = userService.provision(makeProfile("deleted@test.com", "Returning", "User"));

        verify(userRepository).save(any(User.class));
        assertThat(result.getEmail()).isEqualTo("deleted@test.com");
    }

    // --- findById ---

    @Test
    void findById_returnsUser_whenFound() {
        User user = makeUser("found@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(userService.findById(1L)).isSameAs(user);
    }

    @Test
    void findById_throws404_whenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .hasMessageContaining("User not found");
    }

    // --- updateProfile ---

    @Test
    void updateProfile_updatesNamesAndSaves() {
        User user = makeUser("profile@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateProfile(1L, "Updated", "Name");

        assertThat(result.getFirstName()).isEqualTo("Updated");
        assertThat(result.getLastName()).isEqualTo("Name");
        verify(userRepository).save(user);
    }

    // --- updateRole ---

    @Test
    void updateRole_changesRoleAndSaves() {
        User user = makeUser("role@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateRole(1L, Role.STAFF);

        assertThat(result.getRole()).isEqualTo(Role.STAFF);
        verify(userRepository).save(user);
    }

    // --- restore ---

    @Test
    void restore_clearsDeletedAtAndEvictsCache() {
        User deleted = makeUser("restore@test.com");
        deleted.setDeletedAt(java.time.LocalDateTime.now().minusDays(1));
        when(userRepository.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(deleted));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.restore(1L);

        assertThat(result.getDeletedAt()).isNull();
        verify(userStateService).evict("restore@test.com");
    }

    @Test
    void restore_throws400_whenUserIsNotDeleted() {
        User active = makeUser("active@test.com");
        when(userRepository.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> userService.restore(1L))
                .hasMessageContaining("User is not deleted");
    }

    // --- countActiveAdmins ---

    @Test
    void countActiveAdmins_delegatesToRepository() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(3L);

        assertThat(userService.countActiveAdmins()).isEqualTo(3L);
    }

    // --- findAllActive (paginated) ---

    @Test
    void findAllActive_returnsPaginatedResults() {
        var page = new PageImpl<>(List.of(makeUser("a@test.com")), PageRequest.of(0, 50), 1);
        when(userRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        Page<User> result = userService.findAllActive(PageRequest.of(0, 50));

        assertThat(result.getContent()).hasSize(1);
    }

}