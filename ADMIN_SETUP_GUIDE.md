# Admin Dashboard Setup Guide

## Current Status

✅ **Backend:** Running on http://localhost:8080 with hardcoded OTP code `123456`
✅ **Frontend:** Running on http://localhost:5173
✅ **Database:** PostgreSQL running with admin user created

## Admin Account

**Email:** `Jcs7350@g.rit.edu`
**OTP Code:** `123456`
**Database Status:** User ID 4 exists with ADMIN role assigned

## Issue Being Investigated

The frontend should automatically redirect to the admin dashboard (`/admin-dashboard`) when a user with the `MANAGE_ROLES` permission logs in. The admin dashboard is at `/admin-dashboard` and the regular user dashboard is at `/dashboard/home`.

### Steps to Access Admin Dashboard

1. **Sign In:**
   - Go to http://localhost:5173
   - Enter email: `Jcs7350@g.rit.edu`
   - Click "Send Code"
   - Enter OTP: `123456`
   - Click "Continue"

2. **Expected Behavior:**
   - If permissions are loading correctly, you should be automatically redirected to `/admin-dashboard`
   - The admin dashboard shows admin-specific features like user management, role management, etc.

3. **If You See Regular Dashboard Instead:**
   - This means the frontend didn't detect the `MANAGE_ROLES` permission
   - The issue is likely in the `/api/v1/users/me/permissions` backend endpoint not returning the correct permissions
   - Check the browser console (F12) for any API errors
   - Check the backend logs for permission loading issues

## Database Structure

User 4 is setup with:
- Email: `jcs7350@g.rit.edu`
- Auth Subject: `otp:jcs7350@g.rit.edu`
- Status: ACTIVE
- Roles: ADMIN
- ADMIN role includes: MANAGE_ROLES, MANAGE_USERS, MANAGE_EQUIPMENT, MANAGE_RESERVATIONS, REGISTER_USERS, VIEW_ALL_RESERVATIONS

## Quick Diagnostic Commands

### Check Backend Permissions for User:
```bash
curl -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d '{"email":"Jcs7350@g.rit.edu","code":"123456"}' | jq -r '.access_token' > /tmp/token.txt

TOKEN=$(cat /tmp/token.txt)

# Check user
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN" | jq .

# Check permissions
curl -X GET http://localhost:8080/api/v1/users/me/permissions \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### Check Database Directly:
```bash
docker exec makerspace-postgres psql -U app_admin -d collab_app -c "
SELECT u.id, u.email, array_agg(ur.role) as roles, array_agg(rp.permission) as permissions
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN role_permissions rp ON ur.role = rp.role_code
WHERE u.email = 'jcs7350@g.rit.edu'
GROUP BY u.id, u.email;
"
```

## Next Steps

1. **Test the flow** - Sign in and check browser console
2. **Check backend logs** - Look for any permission loading errors
3. **Verify database sync** - Ensure the backend is reading from PostgreSQL correctly
4. **Frontend debugging** - Use React DevTools to inspect AuthContext state after login

