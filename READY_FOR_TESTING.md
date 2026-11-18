# ✅ Card-Based Sign-In System: READY FOR TESTING

## 🎯 What's Been Implemented

### Backend (Java Spring Boot)
✅ **CardAuthController** - New REST controller with 3 endpoints
- `GET /api/v1/auth/card/check-by-uid?uid=CARD_UID` 
  - Check if card is registered/assigned
  - Returns: card status, email, OTP requirement

- `POST /api/v1/auth/card/verify-otp`
  - Verify admin-sent OTP code
  - Create/activate user account
  - Issue JWT token

- `POST /api/v1/auth/card/activate`
  - Mark card as ACTIVATED
  - Assign role to user
  - Requires: JWT token from verify-otp

✅ **Database**
- V17: UnregisteredCard table created
- V18: Role columns added (assignedRole, roleAssignedAt)
- Status values: PENDING → ASSIGNED → ROLE_ASSIGNED → VERIFIED → ACTIVATED

✅ **CardService**
- `verifyOtp()` - Verify admin-sent OTP
- `activateCardAfterOtp()` - Activate card + assign role
- `assignRole()` - Assign role to unregistered card
- `deleteUnregisteredCard()` - Delete for testing

✅ **Security Configuration**
- Added card auth endpoints to SecurityConfig
- Endpoints accessible without authentication (except activate)

### Frontend (React)
✅ **CardSignInForm** - New sign-in component
- Step 1: Tap card
- Step 2: Show email from admin assignment
- Step 3: Enter OTP code
- Step 4: Sign-in to role-appropriate dashboard

✅ **SignIn Page** - Three sign-in tabs
- OAuth (existing)
- Email (existing)  
- Card (new)

✅ **Styling**
- Card form styles
- Tab navigation
- Demo input field for testing

### Admin Dashboard
✅ **Unregistered Cards Management**
- View new cards in "New Cards" tab
- Assign email
- Assign role (MEMBER/STAFF/ADMIN)
- Send OTP code
- Delete card for testing

---

## 🧪 How to Test

### Test Environment
```
Backend: http://localhost:8080
Frontend: http://localhost:5173
```

### Step 1: Admin Sets Up Card

1. Sign in as admin
2. Go to: **Hardware** → **New Cards** tab
3. Create test card (simulate tap or use demo input)
4. Click **"Assign Email"** → Enter `test@example.com`
5. Click **"Assign Role"** → Select `MEMBER`
6. Click **"Send OTP"** → Get code from email/logs (e.g., `123456`)

### Step 2: User Signs In With Card

1. Open new browser tab: http://localhost:5173
2. Click **Sign In**
3. Click **"Card"** tab
4. **Demo mode**: Manually enter card UID (e.g., `85F181D4`)
5. Press Enter to simulate tap
6. Enter OTP code from Step 1 (e.g., `123456`)
7. Click **"Activate Card & Sign In"**
8. ✅ Redirected to dashboard with MEMBER role

### Step 3: Verify Role-Based Access

1. Check dashboard shows member-appropriate content
2. Repeat with different roles (STAFF, ADMIN)
3. Each role should see different dashboard

---

## 🔧 Testing Endpoints with cURL

### Check Card Status
```bash
curl -X GET "http://localhost:8080/api/v1/auth/card/check-by-uid?uid=85F181D4"
```

Response (card not found):
```json
{
  "found": false,
  "message": "Card not found or already registered"
}
```

Response (card assigned):
```json
{
  "found": true,
  "email": "user@example.com",
  "status": "VERIFIED",
  "needsOtp": true,
  "otpGenerated": true
}
```

### Verify OTP
```bash
curl -X POST "http://localhost:8080/api/v1/auth/card/verify-otp" \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "otp": "123456"}'
```

Success response:
```json
{
  "access_token": "eyJhbGc..."
}
```

Error response:
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired OTP"
}
```

### Activate Card
```bash
# Use token from verify-otp response
TOKEN="eyJhbGc..."

curl -X POST "http://localhost:8080/api/v1/auth/card/activate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"cardUid": "85F181D4"}'
```

Response:
```json
{
  "success": "true",
  "message": "Card activated with MEMBER access",
  "role": "MEMBER"
}
```

---

## 📋 Complete User Flow

```
1. Admin Dashboard
   ├─ View new card (status: PENDING)
   ├─ Assign email (status: ASSIGNED)
   ├─ Assign role (status: ROLE_ASSIGNED)
   └─ Send OTP (status: VERIFIED, email sent)

2. User Sign-In Page
   ├─ Click Card tab
   ├─ Tap card (simulated)
   ├─ System checks: GET /api/v1/auth/card/check-by-uid
   │  └─ Response: found=true, email="...", needsOtp=true
   └─ Show email + OTP form

3. User Enters OTP
   ├─ Get code from email
   ├─ Enter 6-digit code
   ├─ Click "Activate Card & Sign In"
   │  ├─ POST /api/v1/auth/card/verify-otp
   │  │  └─ Response: JWT token
   │  ├─ POST /api/v1/auth/card/activate
   │  │  └─ Response: card activated
   │  └─ setToken(JWT)
   └─ Redirect to /dashboard

4. Dashboard
   ├─ Role-appropriate content shown
   ├─ MEMBER: Equipment rental, class booking
   ├─ STAFF: Staff features
   └─ ADMIN: Full admin panel
```

---

## ⚙️ System States

### Card Status Progression
```
PENDING
  ↓ (Assign Email)
ASSIGNED
  ↓ (Assign Role)  
ROLE_ASSIGNED
  ↓ (Send OTP)
VERIFIED
  ↓ (User enters OTP + taps card)
ACTIVATED
```

### User Account Progression
```
No Account
  ↓ (OTP verified)
User Created with Role
  ↓ (Card activated)
User + Card Linked
  ↓ (Next sign-in)
Auto-sign in (future enhancement)
```

---

## 🐛 Troubleshooting

### "Card not found" Error
**Cause**: Card UID doesn't exist or already registered
**Fix**: 
1. Check admin created card
2. Card status should be VERIFIED
3. Card must be assigned email + role

### "Invalid or expired OTP" Error
**Cause**: Wrong code or > 1 hour old
**Fix**:
1. Check admin sent latest OTP
2. Code is 6 digits
3. Copy exactly from email
4. Resend OTP if expired

### "Card not found in database" (Dashboard)
**Cause**: Card not created yet
**Fix**:
1. Admin must tap card first
2. Card appears in "New Cards" tab
3. Then assign email/role/OTP

### Email not received
**Cause**: Email service not configured
**Fix**:
1. Check Redis running: `redis-cli ping`
2. Check SMTP config in `application.properties`
3. Check backend logs for errors
4. Use StubEmailService for testing (logs to console)

---

## 📊 Database Verification

### Check card exists
```sql
SELECT * FROM unregistered_cards 
WHERE card_uid = '85F181D4';
```

### Check user created
```sql
SELECT u.id, u.email, ar.code as role
FROM users u
LEFT JOIN user_app_roles uar ON u.id = uar.user_id
LEFT JOIN app_roles ar ON uar.role = ar.code
WHERE u.email = 'test@example.com';
```

### Check card status history
```sql
SELECT card_uid, status, assigned_to_email, assigned_role, 
       first_tap_at, otp_generated_at, created_at
FROM unregistered_cards
ORDER BY created_at DESC
LIMIT 10;
```

---

## ✅ Verification Checklist

### Backend
- [x] CardAuthController implemented
- [x] GET /api/v1/auth/card/check-by-uid working
- [x] POST /api/v1/auth/card/verify-otp working
- [x] POST /api/v1/auth/card/activate working
- [x] Security rules configured
- [x] Database migrations applied
- [x] Server running on port 8080

### Frontend
- [x] CardSignInForm component created
- [x] SignIn page updated with 3 tabs
- [x] Card tab implemented
- [x] Styling complete
- [x] Demo input field added
- [x] Error handling added
- [x] Server running on port 5173

### Admin Dashboard
- [x] New Cards tab visible
- [x] Assign Email button working
- [x] Assign Role dropdown working
- [x] Send OTP button working
- [x] Delete button for testing
- [x] Role-based access control

---

## 🚀 Next Steps

1. **Test Complete Flow**
   - Create test card via admin
   - Assign email + role + OTP
   - Sign in as user via card
   - Verify dashboard

2. **Test Different Roles**
   - MEMBER role
   - STAFF role
   - ADMIN role
   - Verify each shows correct dashboard

3. **Test Error Scenarios**
   - Wrong OTP code
   - Expired OTP (wait 1+ hour)
   - Card not assigned
   - Card already activated

4. **Real Card Reader Integration**
   - Replace demo input with real NFC reader
   - Test with actual cards
   - Verify rapid taps handled

5. **Email Service Testing**
   - Configure SMTP settings
   - Test OTP emails sent
   - Verify code formatting
   - Check email templates

6. **Security Audit**
   - Review JWT token handling
   - Verify authorization on endpoints
   - Check OTP expiration
   - Test concurrent requests

---

## 📞 Support

**Backend Logs**: `/tmp/backend-test.log`
**Frontend Console**: Browser DevTools (F12)

**Common Commands**:
```bash
# Restart backend
pkill -f "spring-boot:run"
cd backend && nohup ./mvnw spring-boot:run &

# Check Redis
redis-cli ping

# Check database
psql -h localhost -U makerspace -d collaboratory

# View frontend console
# Open browser DevTools: F12 or Cmd+Shift+I
```

---

## 📝 Files Changed

**Backend (Created)**
- `backend/src/main/java/com/makerspace/backend/controller/CardAuthController.java`

**Backend (Modified)**
- `backend/src/main/java/com/makerspace/backend/config/SecurityConfig.java`
- `backend/src/main/java/com/makerspace/backend/services/CardService.java`
- `backend/src/main/java/com/makerspace/backend/services/EmailService.java`

**Frontend (Created)**
- `frontend/src/components/CardSignInForm.jsx`

**Frontend (Modified)**
- `frontend/src/pages/SignIn.jsx`
- `frontend/src/App.css`

**Database**
- `backend/src/main/resources/db/migration/V17__unregistered_cards.sql`
- `backend/src/main/resources/db/migration/V18__add_role_to_unregistered_cards.sql`

---

**Status**: ✅ COMPLETE & READY FOR TESTING
**Date**: 2026-10-25
**Servers**: Backend UP (8080), Frontend UP (5173)
