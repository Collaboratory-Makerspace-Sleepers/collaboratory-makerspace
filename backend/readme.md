What's testable today

User Login / JWT
- JwtService is a standalone component — easy to unit test token generation and validation
- JwtAuthFilter can be tested with MockMvc + MockFilterChain
- UserService.findOrCreate() is straightforward to unit test with a mocked UserRepository

RBAC
- @PreAuthorize annotations on EquipmentController are Spring-native and work with @WithMockUser in tests
- SecurityConfig has clear role rules (MEMBER < STAFF < ADMIN)
- spring-security-test is already in pom.xml

Equipment CRUD
- EquipmentService has clean service/repository separation — mockable with Mockito
- EquipmentRepository has custom finders testable with @DataJpaTest

  ---                                                                                                                                                                                                                               
Blockers to address first

┌───────────────────────────────────────────┬───────────────────────────────────────────────────────────────────────────────────────┐                                                                                             
│                   Issue                   │                                        Impact                                         │                                                                                             
├───────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┤                                                                                             
│ No test database configured               │ @DataJpaTest / integration tests need H2 or Testcontainers                            │
├───────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┤                                                                                             
│ No application-test.properties            │ Context loads with real Auth0 + Postgres credentials                                  │                                                                                             
├───────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┤                                                                                             
│ OAuth2 SuccessHandler end-to-end          │ Needs mocked OidcUser principal                                                       │                                                                                             
├───────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┤                                                                                             
│ Role authority mismatch in HomeController │ Uses "Administrator" instead of "ROLE_ADMIN" — tests could pass but behavior is wrong │                                                                                           
└───────────────────────────────────────────┴───────────────────────────────────────────────────────────────────────────────────────┘
                                                                                                                                                                                                                                  
---                                                                                                                                                                                                                               
Recommended approach

1. Unit tests — JwtService, EquipmentService, UserService with Mockito (no DB needed)
2. Controller tests — @WebMvcTest + @WithMockUser(roles="ADMIN") to verify RBAC on each endpoint
3. Repository tests — @DataJpaTest with H2 in-memory (add H2 to pom.xml as test scope)
4. Skip end-to-end OAuth2 flow tests unless you have Auth0 test credentials 