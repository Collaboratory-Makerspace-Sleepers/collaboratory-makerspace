Fix 1 — JwtAuthFilterTest UnnecessaryStubbing: Moved when(claims.getSubject()).thenReturn("42") out of stubValidToken() and into only the two active-user tests that actually reach that code path. Mockito strict mode rejected
stubs for deleted/unknown user tests that never called getSubject().

Fix 2 — UserSecurityConfig returns 403 instead of 401 for unauthenticated: Added an explicit authenticationEntryPoint that calls res.sendError(401).

Fix 3 — @PreAuthorize SpEL not evaluated in @WebMvcTest: Added a nested @TestConfiguration @EnableMethodSecurity static class MethodSecurityConfig {} inside UserControllerTest. Kept the real UserSecurity bean in @Import (the
@MockBean approach broke SpEL's bean resolver for @userSecurity.isSelf(...) references).

//LAST CLAUDE SESSION.