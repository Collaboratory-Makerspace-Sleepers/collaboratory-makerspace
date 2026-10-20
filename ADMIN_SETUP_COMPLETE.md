# ✅ Admin Dashboard Setup - COMPLETE

## Status
The admin account is now fully configured and working correctly!

## Admin Account Details

**Email:** `jcs7350@g.rit.edu`  
**OTP Code:** `123456` (hardcoded in dev mode)  
**User ID:** 1  
**Role:** ADMIN  
**Permissions:**
- REGISTER_USERS
- MANAGE_EQUIPMENT  
- MANAGE_ROLES ✓ (enables admin dashboard)
- VIEW_ALL_RESERVATIONS
- MANAGE_RESERVATIONS
- MANAGE_USERS

## How to Access

1. **Frontend:** Go to http://localhost:5173
2. **Sign In Page:** Enter email: `jcs7350@g.rit.edu`
3. **Enter OTP:** `123456`
4. **Auto-redirect:** Should automatically redirect to `/admin-dashboard` ✓

## Running Services

### Backend
- **URL:** http://localhost:8080
- **Status:** Running with `APP_OTP_DEV_CODE=123456` and `spring.profiles.active=local`
- **Database:** PostgreSQL on localhost:5434

```bash
cd /Users/zhijunliang/Desktop/code/collaboratory-makerspace/backend
export APP_OTP_DEV_CODE=123456
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run
```

### Frontend
- **URL:** http://localhost:5173
- **Status:** Running with Vite dev server

```bash
cd /Users/zhijunliang/Desktop/code/collaboratory-makerspace/frontend
npm run dev
```

### Database (PostgreSQL)
- **Container:** makerspace-postgres
- **Port:** 5434 (mapped from 5432 inside container)
- **Username:** app_admin
- **Password:** admin123
- **Database:** collab_app
- **Status:** Running

```bash
# Access directly
docker exec makerspace-postgres psql -U app_admin -d collab_app
```

### Redis
- **Container:** makerspace-redis
- **Port:** 6379
- **Status:** Running
- **Usage:** OTP code storage, rate limiting, sessions

## What Was Fixed

### Issue 1: Email Case Sensitivity
- **Problem:** OTP controller normalized email to lowercase, but database queries were case-sensitive
- **Solution:** Ensured database email is stored in lowercase, matching the normalized value from OTP controller
- **Impact:** User lookup now succeeds on first OTP verification

### Issue 2: Hardcoded OTP Code
- **Problem:** Backend needs to accept `123456` as valid OTP in dev mode
- **Solution:** Set `APP_OTP_DEV_CODE=123456` environment variable before starting backend
- **File:** OtpService.java checks `@Value("${APP_OTP_DEV_CODE:}")` configuration
- **Impact:** OTP verification succeeds without actual email delivery

### Issue 3: Database Connection
- **Problem:** Application.properties had wrong default database URL (AWS RDS)
- **Solution:** Used `spring.profiles.active=local` to load application-local.yml with correct localhost:5434 URL
- **Impact:** Backend connects to local Docker PostgreSQL instead of production database

### Issue 4: Admin Permissions Not Returned
- **Problem:** Even with ADMIN role in database, `/api/v1/users/me/permissions` returned empty array
- **Root Cause:** User was being created fresh on each OTP verification instead of finding existing user
- **Solution:** Fixed email lookup so existing users are found correctly
- **Impact:** Permissions now correctly returned from backend

## Database Setup SQL

The admin user was created with:

```sql
-- Create profile
INSERT INTO user_profiles (first_name) VALUES ('Admin') RETURNING id;

-- Create user (ID should be 1)
INSERT INTO users (email, account_status, profile_id, auth0_subject, created_at)
VALUES ('jcs7350@g.rit.edu', 'ACTIVE', 1, 'otp:jcs7350@g.rit.edu', NOW())
RETURNING id;

-- Assign ADMIN role
INSERT INTO user_roles (user_id, role)
VALUES (1, 'ADMIN');

-- Verify setup
SELECT u.id, u.email, array_agg(ur.role) as roles, array_agg(rp.permission) as permissions
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN role_permissions rp ON ur.role = rp.role_code
WHERE u.email = 'jcs7350@g.rit.edu'
GROUP BY u.id, u.email;
```

## Frontend Integration

The Dashboard.jsx component checks for `MANAGE_ROLES` permission:

```javascript
// src/pages/Dashboard.jsx
export function DashboardLanding() {
  const { loadingProfile, hasPermission } = useAuth();
  
  if (loadingProfile) return <p>Loading dashboard…</p>;
  
  // Automatically route to admin dashboard if user has MANAGE_ROLES permission
  return hasPermission("MANAGE_ROLES")
    ? <Navigate to="/admin-dashboard" replace />
    : <Navigate to="/dashboard/home" replace />;
}
```

The AuthContext loads permissions from `/api/v1/users/me/permissions` endpoint.

## Verification Commands

### Check Backend Response
```bash
curl -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d '{"email":"jcs7350@g.rit.edu","code":"123456"}' | jq '.access_token' > /tmp/token.txt

TOKEN=$(cat /tmp/token.txt | tr -d '"')

# User profile (should show role ADMIN)
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN" | jq .

# Permissions (should include MANAGE_ROLES)
curl -X GET http://localhost:8080/api/v1/users/me/permissions \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### Check Database
```bash
docker exec makerspace-postgres psql -U app_admin -d collab_app -c "
SELECT u.id, u.email, u.auth0_subject, 
       array_agg(ur.role) as roles,
       array_agg(rp.permission) as permissions
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN role_permissions rp ON ur.role = rp.role_code
WHERE u.email = 'jcs7350@g.rit.edu'
GROUP BY u.id, u.email, u.auth0_subject;
"
```

## Next Steps

1. ✅ **Sign in to admin account** - Open http://localhost:5173 and use the credentials above
2. ✅ **Verify admin dashboard appears** - Should automatically redirect to `/admin-dashboard`
3. ✅ **Test admin features** - Try managing users, roles, equipment, reservations, etc.
4. ❌ **Stripe checkout testing** - DAY_PASS product is archived in Stripe (separate issue)

## Notes

- The OTP code `123456` only works when `APP_OTP_DEV_CODE=123456` environment variable is set
- This is development-only configuration - production would use actual email delivery
- All services (backend, frontend, database, Redis) must be running for full functionality
- The admin permissions are read from the role_permissions table configured during database migrations

