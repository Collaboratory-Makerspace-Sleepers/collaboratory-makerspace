package com.makerspace.backend;

import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.services.UserStateService;
import com.makerspace.backend.services.UserStateService.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStateServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserStateService userStateService;

    private User activeUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(Role.MEMBER);
        // deletedAt defaults to null
        return user;
    }

    private User deletedUser(String email) {
        User user = activeUser(email);
        user.setDeletedAt(LocalDateTime.now().minusHours(1));
        return user;
    }

    @Test
    void stateOf_returnsActive_forUserWithNullDeletedAt() {
        when(userRepository.findByEmailIncludingDeleted("active@test.com"))
                .thenReturn(Optional.of(activeUser("active@test.com")));

        assertThat(userStateService.stateOf("active@test.com")).isEqualTo(State.ACTIVE);
    }

    @Test
    void stateOf_returnsDeleted_forUserWithDeletedAtSet() {
        when(userRepository.findByEmailIncludingDeleted("deleted@test.com"))
                .thenReturn(Optional.of(deletedUser("deleted@test.com")));

        assertThat(userStateService.stateOf("deleted@test.com")).isEqualTo(State.DELETED);
    }

    @Test
    void stateOf_returnsNotFound_forUnknownEmail() {
        when(userRepository.findByEmailIncludingDeleted("ghost@test.com"))
                .thenReturn(Optional.empty());

        assertThat(userStateService.stateOf("ghost@test.com")).isEqualTo(State.NOT_FOUND);
    }
}