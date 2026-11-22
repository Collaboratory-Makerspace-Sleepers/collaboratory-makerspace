# In-person registration via admin panel (Auth0 / Google OAuth)

## Summary

Staff pre-register a customer (creating the application-side profile before the
person ever logs in), optionally sending an invite link. The person later
authenticates with Google via Auth0; the pending profile is **linked to that
identity only when the Auth0-verified email matches the pre-registered email**.

Two mechanisms cooperate, both gated on the verified email:

1. **Resolution-time auto-claim** (the safety net): during JWT resolution, a
   pending account whose email matches the verified login email is activated and
   its `auth0_subject` is set.
2. **Token-bound claim** (the in-person path): the invite link carries a
   single-use token. After login the frontend POSTs it to `/api/registrations/claim`,
   which consumes the invite, applies the email gate, and surfaces mismatches for
   staff instead of silently linking the wrong identity.

The token never authorizes binding on its own — possession of the link plus a
matching verified email is required. This keeps the trust anchor on Google's
verified email while giving you an auditable, revocable, expiring invite.

---

## Open decisions (resolve while implementing)

- **No-record branch.** A Google login with no matching pending record: either
  self-provision a `GUEST` (staff upgrade later) or reject because every account
  is admin-initiated. This determines the `else` branch in resolution. *Marked
  `// DECISION` in the resolution skeleton below.*
- **Claim mode.** Default is auto-claim on email match at login. If you want the
  account inert until the link is used, set `registration.require-explicit-claim=true`
  so pending accounts get `ROLE_PENDING` and only `/claim` activates them.
- **Token TTL / resend / cleanup.** Suggested 7-day expiry, staff-triggered
  resend, and a scheduled job to purge expired unconsumed invites.
- **Email transport.** Assumed SES; verify the sending domain/from-address.
- **CSRF on `/claim`.** Cookie-borne JWT + state-changing endpoint needs SameSite
  or explicit CSRF protection (your existing concern).
- **Walk-ins without a Google account.** Out of scope unless you add a database
  or passwordless Auth0 connection as a fallback.

### Prerequisites to confirm first

- `claims.getSubject()` returns the Auth0 subject string (e.g. `google-oauth2|123…`),
  and you map it to the `auth0_subject` column — not a numeric DB id.
- `auth0_subject` is `UNIQUE` and indexed (nullable).

---

## 1. Schema

Apply via your migration tool (Flyway/Liquibase).

```sql
-- Account lifecycle. Existing rows default to ACTIVE; pre-registrations set
-- PRE_REGISTERED explicitly.
ALTER TABLE users
  ADD COLUMN account_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- Ensure auth0_subject is unique + indexed + nullable (confirm it exists).
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_auth0_subject
  ON users (auth0_subject) WHERE auth0_subject IS NOT NULL;

-- Case-insensitive uniqueness among live rows (respects your soft-delete).
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower
  ON users (lower(email)) WHERE deleted_at IS NULL;

CREATE TABLE registration_invites (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT       NOT NULL REFERENCES users(id),
  token_hash      CHAR(64)     NOT NULL,        -- SHA-256 hex of the raw token
  intended_email  VARCHAR(320) NOT NULL,        -- snapshot of the email staff entered
  expires_at      TIMESTAMPTZ  NOT NULL,
  consumed_at     TIMESTAMPTZ,
  created_by      BIGINT       REFERENCES users(id),
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_reg_invites_token_hash ON registration_invites (token_hash);
CREATE INDEX        ix_reg_invites_user       ON registration_invites (user_id);
```

The raw token is never stored or logged — only its SHA-256 hash, like a
password-reset token.

---

## 2. Entities

```java
public enum AccountStatus { PRE_REGISTERED, ACTIVE }
```

On `User`:

```java
@Enumerated(EnumType.STRING)
@Column(name = "account_status", nullable = false)
private AccountStatus accountStatus = AccountStatus.ACTIVE;

@Column(name = "auth0_subject", unique = true)
private String auth0Subject;   // null until claimed
```

New entity:

```java
@Entity
@Table(name = "registration_invites")
public class RegistrationInvite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "intended_email", nullable = false)
    private String intendedEmail;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
    public void consume() { this.consumedAt = Instant.now(); }
}
```

---

## 3. DTOs

```java
public record PreRegisterRequest(
    @Email @NotBlank String email,
    @NotBlank String fullName,
    Set<Role> authorityRoles,          // INSTRUCTOR / STAFF / ADMIN only
    Set<EligibilityType> eligibilityTypes,
    boolean sendInvite
) {}

public record PreRegisteredUserResponse(
    Long id, String email, String fullName,
    AccountStatus status, Set<Role> authorityRoles,
    Set<EligibilityType> eligibilityTypes, boolean inviteSent
) {}

public record ClaimRequest(@NotBlank String token) {}
// Claim returns your existing /me DTO (e.g. CurrentUserResponse).
```

---

## 4. Repositories

```java
interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAuth0Subject(String subject);

    boolean existsByEmailIncludingDeleted(String email);     // native, bypasses @SQLRestriction
    Optional<User> findByEmailIncludingDeleted(String email); // existing

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);  // serializes concurrent claims
}

interface RegistrationInviteRepository extends JpaRepository<RegistrationInvite, Long> {
    Optional<RegistrationInvite> findByTokenHash(String tokenHash);
}
```

---

## 5. Services

### InviteTokenService

```java
@Service
public class InviteTokenService {
    private static final SecureRandom RNG = new SecureRandom();

    @Value("${registration.invite-ttl:P7D}")
    private Duration ttl;

    public record Issued(String rawToken, String tokenHash, Instant expiresAt) {}

    public Issued issue() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        return new Issued(raw, sha256Hex(raw), Instant.now().plus(ttl));
    }

    public String hash(String raw) { return sha256Hex(raw); }

    private static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                .digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
```

### AdminRegistrationService

```java
@Service
public class AdminRegistrationService {
    // constructor-injected: userRepository, inviteRepository,
    // inviteTokenService, emailService

    @Transactional
    public PreRegisteredUserResponse preRegister(PreRegisterRequest req, Long adminUserId) {
        String email = normalize(req.email());
        if (userRepository.existsByEmailIncludingDeleted(email))
            throw new ConflictException("An account with this email already exists");

        User u = new User();
        u.setEmail(email);
        u.setFullName(req.fullName());
        u.setAccountStatus(AccountStatus.PRE_REGISTERED);
        u.setAuth0Subject(null);
        u.setAuthorityRoles(requireAuthorityRoles(req.authorityRoles())); // reject non-authority enums
        u.setEligibilityTypes(req.eligibilityTypes() == null ? Set.of() : req.eligibilityTypes());
        userRepository.save(u);

        boolean invited = false;
        if (req.sendInvite()) { issueAndSendInvite(u, adminUserId); invited = true; }
        return toResponse(u, invited);
    }

    @Transactional
    public void resendInvite(Long userId, Long adminUserId) {
        User u = userRepository.findById(userId).orElseThrow(NotFoundException::new);
        if (u.getAccountStatus() != AccountStatus.PRE_REGISTERED)
            throw new ConflictException("Account is already active");
        issueAndSendInvite(u, adminUserId);
    }

    private void issueAndSendInvite(User u, Long adminUserId) {
        var issued = inviteTokenService.issue();
        var invite = new RegistrationInvite();
        invite.setUserId(u.getId());
        invite.setTokenHash(issued.tokenHash());
        invite.setIntendedEmail(u.getEmail());
        invite.setExpiresAt(issued.expiresAt());
        invite.setCreatedBy(adminUserId);
        inviteRepository.save(invite);
        emailService.sendRegistrationInvite(u.getEmail(), u.getFullName(), issued.rawToken());
    }

    private Set<Role> requireAuthorityRoles(Set<Role> roles) {
        if (roles == null) return Set.of();
        roles.forEach(r -> { if (!r.isAuthorityRole())
            throw new BadRequestException(r + " is not an authority role"); });
        return roles;
    }
}
```

`Role.isAuthorityRole()` should return true only for INSTRUCTOR/STAFF/ADMIN —
eligibility types are a separate axis and must not be granted here.

### AccountClaimService

```java
@Service
public class AccountClaimService {
    // constructor-injected: userRepository, inviteRepository,
    // inviteTokenService, currentUserMapper, (optional) reviewService

    @Transactional
    public CurrentUserResponse claim(UserPrincipal principal, String rawToken) {
        RegistrationInvite invite = inviteRepository
            .findByTokenHash(inviteTokenService.hash(rawToken))
            .orElseThrow(InvalidTokenException::new);
        if (!invite.isUsable(Instant.now()))
            throw new InvalidTokenException("This invite has expired or already been used");

        // Lock the target row so two concurrent claims serialize.
        User user = userRepository.findByIdForUpdate(invite.getUserId())
            .orElseThrow(NotFoundException::new);

        if (user.getDeletedAt() != null)
            throw new ForbiddenException("This account is closed — please contact staff");

        // Idempotent: same identity re-submitting the link.
        if (user.getAccountStatus() == AccountStatus.ACTIVE
                && principal.subject().equals(user.getAuth0Subject())) {
            invite.consume();
            return currentUserMapper.toResponse(user);
        }
        if (user.getAuth0Subject() != null
                && !principal.subject().equals(user.getAuth0Subject()))
            throw new ConflictException("This registration is already linked to another account");

        // THE GATE — verified email must match the pre-registered email.
        if (!normalize(principal.email()).equals(normalize(user.getEmail()))) {
            // Do NOT silently link. Surface for staff; resolution is to correct
            // the record's email and re-invite.
            // reviewService.flagMismatch(invite, principal);
            throw new EmailMismatchException(
                "You signed in as " + principal.email()
                + " but were invited as " + user.getEmail());
        }

        user.setAuth0Subject(principal.subject());
        user.setAccountStatus(AccountStatus.ACTIVE);
        invite.consume();
        return currentUserMapper.toResponse(user);
    }
}
```

Catch `DataIntegrityViolationException` on the `auth0_subject`/`token_hash` unique
constraints at the controller/advice layer as a backstop for races the lock
doesn't cover.

---

## 6. Resolution-time auto-claim (JwtAuthFilter / UserStateService)

Extend the existing per-request resolution that turns a validated Auth0 JWT into
a `UserPrincipal`. Do the mutating auto-claim in a small `@Transactional` method
with `findByIdForUpdate`, not inside the filter directly.

```java
// After validating the Auth0 JWT: subject, verifiedEmail
User user = userRepository.findByAuth0Subject(subject).orElse(null);

if (user == null) {
    User byEmail = userRepository.findByEmailIncludingDeleted(normalize(verifiedEmail))
        .orElse(null);

    if (byEmail != null && byEmail.getDeletedAt() != null)
        throw new ForbiddenException("contact staff");

    if (byEmail != null && byEmail.getAuth0Subject() == null) {
        if (requireExplicitClaim) {
            user = byEmail;                       // stays PRE_REGISTERED → ROLE_PENDING
        } else {
            user = userStateService.autoClaimByEmail(byEmail.getId(), subject); // sets subject, ACTIVE
        }
    } else if (byEmail == null) {
        // DECISION: self-provision a GUEST, or reject.
        // user = userStateService.selfProvisionGuest(subject, verifiedEmail);
        // throw new ForbiddenException("No registration found — please see staff");
    }
}

if (user != null && user.getDeletedAt() != null) throw new ForbiddenException("contact staff");

// PENDING accounts get a restricted authority so they can only reach /claim and /me.
UserPrincipal principal = UserPrincipal.from(user);  // ROLE_PENDING when PRE_REGISTERED
```

`autoClaimByEmail` mirrors the gate trivially (the row was found *by* the verified
email), so it can set `auth0_subject` and flip to `ACTIVE` under the row lock.

---

## 7. Controllers

```java
@RestController
@RequestMapping("/api/admin/registrations")
class AdminRegistrationController {
    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    ResponseEntity<PreRegisteredUserResponse> preRegister(
            @Valid @RequestBody PreRegisterRequest req, Authentication auth) {
        var res = service.preRegister(req, currentUserId(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PostMapping("/{userId}/resend-invite")
    @PreAuthorize("hasRole('STAFF')")
    ResponseEntity<Void> resend(@PathVariable Long userId, Authentication auth) {
        service.resendInvite(userId, currentUserId(auth));
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/api/registrations")
class RegistrationClaimController {
    @PostMapping("/claim")
    CurrentUserResponse claim(@Valid @RequestBody ClaimRequest req, Authentication auth) {
        return claimService.claim((UserPrincipal) auth.getPrincipal(), req.token());
    }
}
```

Thin controllers delegating to the transactional services; `currentUserId(auth)`
reuses your `/me` resolution.

---

## 8. Security config

In the relevant per-module chain (`securityMatcher` + `@Order`):

```java
.authorizeHttpRequests(reg -> reg
    .requestMatchers(HttpMethod.POST, "/api/admin/registrations/**").hasRole("STAFF")
    .requestMatchers(HttpMethod.POST, "/api/registrations/claim").authenticated() // incl. ROLE_PENDING
    .requestMatchers("/api/users/me").authenticated()
    // ...everything else requires an ACTIVE authority; ROLE_PENDING is rejected
)
```

- Confirm the `RoleHierarchy` bean applies to both the filter-chain routes and
  `@PreAuthorize` method security (already on your open-items list).
- `/claim` is state-changing over a cookie JWT → apply CSRF protection or rely on
  `SameSite`; send the token in the request body, not the URL.

---

## 9. Email / invite delivery

```java
public interface EmailService {
    void sendRegistrationInvite(String toEmail, String fullName, String rawToken);
}
```

Link target: `${app.frontend-base-url}/register/confirm?token=${rawToken}`.
SES implementation; verify domain/from-address. Never log the raw token.

---

## 10. Frontend (auth0-react v2)

Invite landing route reads the token and carries it through the OAuth round trip
via `appState` (top-level `connection` is silently ignored — it must be nested):

```jsx
// /register/confirm?token=...
const { loginWithRedirect } = useAuth0();
const token = new URLSearchParams(window.location.search).get("token");

loginWithRedirect({
  authorizationParams: { connection: "google-oauth2" },
  appState: { claimToken: token },
});
```

In the `Auth0Provider` callback, POST the recovered token:

```jsx
onRedirectCallback={(appState) => {
  if (appState?.claimToken) {
    fetch("/api/registrations/claim", {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf },
      body: JSON.stringify({ token: appState.claimToken }),
    });
  }
}}
```

Same `connection` nesting applies to `withAuthenticationRequired` on the route.

---

## 11. Concurrency & idempotency

- `findByIdForUpdate` (PESSIMISTIC_WRITE) on the target row serializes concurrent
  claims, matching the pattern used for the last-admin guard.
- Unique constraints on `auth0_subject` and `token_hash` are the backstop; map
  `DataIntegrityViolationException` to a 409 and re-read.
- Re-claim by the same identity is a no-op success; claim by a different identity
  on an already-linked record is a 409.

---

## 12. Tests to write

- `preRegister`: rejects duplicate email including soft-deleted; rejects
  non-authority roles; persists `PRE_REGISTERED` with `auth0_subject = null`;
  issues + sends invite only when requested.
- `claim` happy path: matching verified email sets subject, flips to ACTIVE,
  consumes the invite.
- `claim` rejects: expired token, consumed token, unknown token.
- `claim` email mismatch: rejected, record left unlinked, invite not consumed.
- `claim` on deleted user: 403.
- `claim` already linked to another subject: 409.
- `claim` idempotent: same subject re-submitting returns success without
  double-consume side effects.
- Resolution: auto-claim links subject on email match; deleted → 403; no-record
  branch behaves per chosen policy; PENDING accounts can reach `/claim` and `/me`
  but nothing else.
- Concurrency: two simultaneous claims → exactly one links; the other sees
  consumed/active.

---

## 13. Ordered checklist

1. Migration: `account_status`, `auth0_subject` unique index, email lower index,
   `registration_invites`.
2. Entity + `AccountStatus` enum; map `auth0_subject` unique/nullable.
3. Repositories: `findByAuth0Subject`, `findByIdForUpdate`, invite repo.
4. `InviteTokenService`.
5. `EmailService` invite template + SES wiring.
6. `AdminRegistrationService` + controller + `hasRole('STAFF')` + authority-role
   validation.
7. `UserPrincipal` ROLE_PENDING authority + resolution changes (auto-claim /
   `require-explicit-claim` toggle); resolve the no-record decision.
8. `AccountClaimService` + claim controller + gate + lock + idempotency.
9. Security chain rules + CSRF on `/claim`.
10. Frontend invite route + `appState` claim POST.
11. Tests.
12. Optional: mismatch review flag + admin view; scheduled cleanup of expired
    unconsumed invites.