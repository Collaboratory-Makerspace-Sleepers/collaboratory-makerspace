package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.config.security.UserSecurity;
import com.makerspace.backend.controller.dto.UpdateProfileRequest;
import com.makerspace.backend.controller.dto.UpdateRoleRequest;
import com.makerspace.backend.controller.dto.UserAdminDTO;
import com.makerspace.backend.controller.dto.UserDTO;
import com.makerspace.backend.services.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserSecurity userSecurity;

    // -------------------------------------------------------------------------
    // /me — current user's own profile
    // -------------------------------------------------------------------------

    @GetMapping("/me")
    public UserDTO getMe(Authentication auth) {
        return UserDTO.from(userService.findById(currentUserId(auth)));
    }

    @PatchMapping("/me")
    public UserDTO updateMe(@Valid @RequestBody UpdateProfileRequest req, Authentication auth) {
        return UserDTO.from(userService.updateProfile(currentUserId(auth), req.firstName(), req.lastName()));
    }

    // -------------------------------------------------------------------------
    // Staff / admin user management
    // -------------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public Page<UserAdminDTO> listUsers(@PageableDefault(size = 50) Pageable pageable) {
        return userService.findAllActive(pageable).map(UserAdminDTO::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN') or @userSecurity.isSelf(#id, authentication)")
    public UserDTO getUser(@PathVariable Long id) {
        return UserDTO.from(userService.findById(id));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDTO updateRole(@PathVariable Long id,
                              @Valid @RequestBody UpdateRoleRequest req,
                              Authentication auth) {
        if (currentUserId(auth).equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change your own role");
        }
        return UserDTO.from(userService.updateRole(id, req.role()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isSelf(#id, authentication)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                           Authentication auth,
                                           HttpServletResponse response) {
        boolean self = userSecurity.isSelf(id, auth);
        Long actorId = userSecurity.getUserId(auth);

        userService.softDeleteUser(id, actorId);

        if (self) {
            clearAuthCookie(response);
        }

        return ResponseEntity.noContent().build();
    }

    private void clearAuthCookie(HttpServletResponse response) {
        Cookie cleared = new Cookie("access_token", "");
        cleared.setHttpOnly(true);
        cleared.setSecure(true);
        cleared.setPath("/");
        cleared.setMaxAge(0);
        response.addCookie(cleared);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDTO restoreUser(@PathVariable Long id) {
        return UserDTO.from(userService.restore(id));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Long currentUserId(Authentication auth) {
        return ((UserPrincipal) auth.getPrincipal()).userId();
    }
}