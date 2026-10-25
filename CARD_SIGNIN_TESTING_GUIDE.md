# Card-Based Sign-In Testing Guide

## Overview
This guide walks through testing the complete card-based sign-in workflow for the Collaboratory Makerspace unregistered card registration system.

## System Architecture

### Backend Endpoints
- **GET /api/v1/auth/card/check-by-uid** - Check if card needs OTP verification
- **POST /api/v1/auth/card/verify-otp** - Verify admin-sent OTP and create/activate user
- **POST /api/v1/auth/card/activate** - Activate card after OTP verification

### Frontend Components
- **SignIn.jsx** - Updated with tabs for OAuth, Email, and Card sign-in modes
- **CardSignInForm.jsx** - New card-based sign-in component

## Testing Workflow

### Prerequisites
1. Backend running on http://localhost:8080
2. Frontend running on http://localhost:5173
3. PostgreSQL database with migrations applied
4. Email service configured (Redis + SMTP)

### Step 1: Access Admin Dashboard and Create Test Card

1. Open browser and navigate to http://localhost:5173
2. Sign in as an admin user
3. Go to **Hardware > New Cards** tab
4. Simulate a new card tap using the card reader
5. You should see a new entry in the "PENDING" status

Example response when checking unregistered cards:
```json
{
  "id": 1,
  "cardUid": "85F181D4",
  "status": "PENDING",
  "assignedToEmail": null,
  "assignedRole": null,
  "firstTapAt": "2026-10-25T13:24:04Z",
  "lastTapAt": "2026-10-25T13:24:04Z",
  "tapCount": 1,
  "deviceId": "reader-1"
}
```

### Step 2: Assign Email to Card

1. In the Admin Dashboard "New Cards" tab
2. Click **"Assign Email"** button for the pending card
3. Enter a test email (e.g., `test-user@example.com`)
4. Click confirm
5. Card status changes to "ASSIGNED"

### Step 3: Assign Role to Card

1. Click **"Assign Role"** dropdown (only shows after email is assigned)
2. Select a role: `MEMBER`, `STAFF`, or `ADMIN`
3. Click confirm
4. Card status changes to "ROLE_ASSIGNED"

### Step 4: Send OTP to User Email

1. Click **"Send OTP"** button (only shows after role is assigned)
2. System generates 6-digit random code
3. Backend sends email with code to assigned email address
4. Card status changes to "VERIFIED"
5. OTP code stored in database with 1-hour expiration

Example OTP email:
```
Subject: Your Collaboratory Card Activation Code

Your one-time code is: 123456

This code is valid for 1 hour.
```

### Step 5: User Signs In Using Card (Frontend)

1. Open new browser/private window to http://localhost:5173
2. Click **Sign In** button
3. Click **"Card"** tab (new tab added)
4. **Demo mode**: Manually enter card UID `85F181D4` (or actual card UID)
5. Press Enter to simulate card tap
6. System checks if card exists via `GET /api/v1/auth/card/check-by-uid?uid=85F181D4`

Expected response:
```json
{
  "found": true,
  "email": "test-user@example.com",
  "status": "VERIFIED",
  "needsOtp": true,
  "otpGenerated": true
}
```

7. Frontend shows:
   - Email field (read-only): `test-user@example.com`
   - OTP input field with placeholder "6-digit code"
   - Message: "Card found! Enter the one-time code that was sent to your email."

### Step 6: User Enters OTP Code

1. User checks email for the OTP code sent in Step 4
2. Enters the 6-digit code in the OTP field
3. Clicks **"Activate Card"** button

### Step 7: Backend Verifies OTP

Frontend calls `POST /api/v1/auth/card/verify-otp`:
```json
{
  "email": "test-user@example.com",
  "otp": "123456"
}
```

Backend performs:
1. Finds UnregisteredCard by email
2. Validates OTP code against stored code
3. Checks OTP expiration (must be < 1 hour old)
4. Creates or activates user account
5. Applies assigned role to user (MEMBER/STAFF/ADMIN)
6. Issues JWT token

Expected response:
```json
{
  "access_token": "eyJhbGc..."
}
```

### Step 8: Card Activation

Frontend automatically calls `POST /api/v1/auth/card/activate`:
```json
{
  "cardUid": "85F181D4"
}
```

Backend performs:
1. Extracts authenticated user from JWT token
2. Finds UnregisteredCard by UID
3. Marks card as "ACTIVATED"
4. Records activation timestamp

Expected response:
```json
{
  "success": "true",
  "message": "Card activated with MEMBER access",
  "role": "MEMBER"
}
```

### Step 9: User Redirects to Dashboard

1. Frontend receives JWT token and stores in auth context
2. Automatically redirects to `/dashboard`
3. Dashboard shows content appropriate to assigned role:
   - **MEMBER**: Regular member dashboard (rent equipment, book classes)
   - **STAFF**: Staff dashboard with additional management features
   - **ADMIN**: Admin dashboard with user/card management

### Step 10: Verify Card Status in Admin Dashboard

1. Return to admin dashboard
2. Go to **Hardware > New Cards** tab
3. Card status should now be "ACTIVATED"
4. Card shows assigned email and role

## Endpoint Testing with cURL

### Test 1: Check Card Status
```bash
curl -X GET "http://localhost:8080/api/v1/auth/card/check-by-uid?uid=85F181D4"
```

Expected for non-existent card:
```json
{
  "found": false,
  "message": "Card not found or already registered"
}
```

### Test 2: Verify OTP
```bash
curl -X POST "http://localhost:8080/api/v1/auth/card/verify-otp" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test-user@example.com",
    "otp": "123456"
  }'
```

Expected for valid OTP:
```json
{
  "access_token": "eyJhbGci..."
}
```

Expected for invalid OTP:
```json
{
  "timestamp": "2026-10-25T17:45:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired OTP",
  "path": "/api/v1/auth/card/verify-otp"
}
```

### Test 3: Activate Card (requires JWT token from Test 2)
```bash
curl -X POST "http://localhost:8080/api/v1/auth/card/activate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "cardUid": "85F181D4"
  }'
```

Expected:
```json
{
  "success": "true",
  "message": "Card activated with MEMBER access",
  "role": "MEMBER"
}
```

## Testing Different Scenarios

### Scenario 1: Wrong OTP Code
1. Follow Steps 1-5
2. Enter wrong code (e.g., `000000`)
3. Expected error: "That code is invalid or expired. Please try again."

### Scenario 2: Expired OTP (>1 hour old)
1. Wait longer than 1 hour from OTP generation
2. Attempt to enter OTP code
3. Expected error: "Invalid or expired OTP"

### Scenario 3: Card Not Yet Assigned
1. Tap a new card
2. Don't assign email/role
3. Card status is "PENDING"
4. When checking card: `needsOtp: false`
5. Expected error: "This card has not been assigned by an admin yet."

### Scenario 4: Card Already Registered
1. Previously activated card UID
2. When checking card: `found: false`
3. Expected error: "Card not found or already registered"

### Scenario 5: Different Roles
1. Repeat flow with role: "STAFF"
2. After activation, user redirected to staff dashboard
3. Repeat flow with role: "ADMIN"
4. After activation, user redirected to admin dashboard

## Troubleshooting

### Issue: "Card not found" even after admin creates it
- **Solution**: Check that card UID matches exactly (case-sensitive, no spaces)
- **Verify**: Admin dashboard should show card in "PENDING" status
- **Check logs**: Backend logs should show card creation

### Issue: OTP email not received
- **Solution**: Check email service configuration
  1. Verify Redis is running: `redis-cli ping`
  2. Verify SMTP credentials in `application.properties`
  3. Check backend logs for email send errors
- **For testing**: Use StubEmailService to log codes to console

### Issue: User created but role not assigned
- **Solution**: Role is assigned during card activation, not creation
- **Verify**: Check UnregisteredCard table for `assigned_role` column
- **Check logs**: Look for "Card activated with X access" message

### Issue: JWT token not working for activate endpoint
- **Solution**: Token must be issued by `/api/v1/auth/card/verify-otp`
- **Verify**: Token payload should have `sub: "card:email@example.com"`
- **Check logs**: Look for authentication errors

## Database Queries for Testing

### View all unregistered cards:
```sql
SELECT * FROM unregistered_cards ORDER BY first_tap_at DESC;
```

### View specific card:
```sql
SELECT * FROM unregistered_cards WHERE card_uid = '85F181D4';
```

### View user created from card:
```sql
SELECT u.id, u.email, u.auth0_subject, ar.code as role
FROM users u
LEFT JOIN user_app_roles uar ON u.id = uar.user_id
LEFT JOIN app_roles ar ON uar.role = ar.code
WHERE u.email = 'test-user@example.com';
```

### Delete test card for retesting:
```sql
DELETE FROM unregistered_cards WHERE card_uid = '85F181D4';
```

Or use Admin Dashboard "Delete & Reset" button.

## Success Criteria Checklist

- [x] Backend CardAuthController compiles without errors
- [x] Backend starts successfully on port 8080
- [x] CardSignInForm component created in frontend
- [x] SignIn page has three tabs: OAuth, Email, Card
- [x] GET /api/v1/auth/card/check-by-uid endpoint working
- [ ] Admin can create test cards via dashboard
- [ ] Admin can assign email and role to card
- [ ] Admin can send OTP code via email
- [ ] User can tap card and see sign-in form
- [ ] User can enter OTP code and verify
- [ ] Card is marked as ACTIVATED after verification
- [ ] User is redirected to role-appropriate dashboard
- [ ] Role is correctly applied to user account

## Next Steps

1. **Card Reader Integration**: Replace demo input with actual NFC card reader
2. **Email Service Verification**: Ensure OTP emails are being sent correctly
3. **Session Management**: Ensure JWT token is properly stored and refreshed
4. **Error Handling**: Add more detailed error messages for edge cases
5. **Analytics**: Track card activation success/failure rates
6. **Security**: Review and harden card authentication endpoints
7. **Performance**: Monitor database queries for optimization

## Integration Points

### Email Service
- **File**: `backend/src/main/java/com/makerspace/backend/services/EmailService.java`
- **Method**: `sendCardActivationOtp(String email, String code)`
- **Status**: Implemented, needs testing with actual email service

### User Service
- **File**: `backend/src/main/java/com/makerspace/backend/services/UserService.java`
- **Methods**: `resolve()`, `provision()`, `autoClaimByEmail()`
- **Status**: Existing methods reused for card auth flow

### Card Service
- **File**: `backend/src/main/java/com/makerspace/backend/services/CardService.java`
- **Key Methods**: `verifyOtp()`, `activateCardAfterOtp()`, `assignRole()`
- **Status**: All implemented and compiled

### Frontend Auth Context
- **File**: `frontend/src/context/AuthContext.jsx`
- **Update Needed**: Store card UID in session during card-based sign-in
- **Purpose**: Enable backend to match card with user during activation

## Demo Test Credentials

Use these emails for testing:
- `test-member@makerspace.local`
- `test-staff@makerspace.local`
- `test-admin@makerspace.local`

Use these card UIDs (simulate with demo input):
- `85F181D4` (primary test card)
- `ABCD1234` (secondary test card)
- `TEST0001` (tertiary test card)
