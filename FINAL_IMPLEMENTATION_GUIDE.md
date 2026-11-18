# Card-Based Sign-In: Complete Implementation Guide

## 🎯 System Overview

This implementation provides a complete card-based sign-in system for the Collaboratory Makerspace with role-based access control.

### Two Sign-In Flows

#### Flow 1: Email Sign-In
```
User enters email → Receives OTP code → Enters code → Signs in with role-appropriate dashboard
```

#### Flow 2: Card Sign-In
```
Tap Card → System checks status → Two outcomes:
  - REGISTERED CARD: Auto-sign in with role-based dashboard
  - UNREGISTERED CARD: Show email + OTP form → Admin-sent code required → Sign in
```

---

## 📋 Complete Workflow

### Admin: Setting Up New Cards

1. **Card is tapped** at hardware reader
   - Status: `PENDING`
   - Shows in Admin Dashboard > Hardware > New Cards

2. **Admin assigns email**
   - Status changes to: `ASSIGNED`
   - Admin clicks "Assign Email" and enters user's email
   - Status: `ASSIGNED`

3. **Admin assigns role**
   - Status changes to: `ROLE_ASSIGNED`
   - Admin clicks "Assign Role" and selects: `MEMBER`, `STAFF`, or `ADMIN`
   - Status: `ROLE_ASSIGNED`

4. **Admin sends OTP code**
   - Status changes to: `VERIFIED`
   - Admin clicks "Send OTP"
   - System generates 6-digit random code
   - Email sent to user with the code
   - Status: `VERIFIED` (OTP valid for 1 hour)

### User: First Sign-In (New Card)

**Scenario: User has new unregistered card**

1. **User navigates to Sign-In page**
   - URL: http://localhost:5173/signin
   - Three tabs available: OAuth, Email, Card

2. **User clicks "Card" tab**
   - See message: "Tap your NFC card to sign in"
   - Option to manually enter card UID (demo mode)

3. **User taps card**
   - System calls: `GET /api/v1/auth/card/check-by-uid?uid=CARD_UID`
   - Two possible responses:

   **A) Card found & assigned:**
   ```json
   {
     "found": true,
     "email": "user@example.com",
     "status": "VERIFIED",
     "needsOtp": true,
     "otpGenerated": true
   }
   ```
   - Form updates to show email and OTP input field

   **B) Card not found or not assigned:**
   ```json
   {
     "found": false,
     "message": "Card not found or already registered"
   }
   ```
   - Error: "Card not found in system. Please contact admin for access."

4. **User enters OTP code**
   - Code received from email
   - Example: `123456`
   - Shows hint: "Check the email sent by your admin"

5. **User clicks "Activate Card & Sign In"**
   - Frontend calls: `POST /api/v1/auth/card/verify-otp`
   - Request body:
   ```json
   {
     "email": "user@example.com",
     "otp": "123456"
   }
   ```

   Backend processes:
   - Finds UnregisteredCard by email
   - Validates OTP code (must match + not expired)
   - Creates user account if doesn't exist
   - Applies assigned role to user
   - Issues JWT token

   Response:
   ```json
   {
     "access_token": "eyJhbGc..."
   }
   ```

6. **Frontend receives JWT and auto-calls activate**
   - Frontend calls: `POST /api/v1/auth/card/activate`
   - Request body:
   ```json
   {
     "cardUid": "85F181D4"
   }
   ```

   Backend processes:
   - Extracts user from JWT token
   - Marks card as: `ACTIVATED`
   - Records activation timestamp

   Response:
   ```json
   {
     "success": "true",
     "message": "Card activated with MEMBER access",
     "role": "MEMBER"
   }
   ```

7. **User redirected to dashboard**
   - Role-appropriate dashboard shown:
     - `MEMBER`: Regular member dashboard (rent equipment, book classes)
     - `STAFF`: Staff dashboard (staff features)
     - `ADMIN`: Admin dashboard (full management access)

### User: Subsequent Sign-Ins (Registered Card)

**Scenario: User already has activated card**

1. User taps card
2. System checks: Card is ACTIVATED
3. System auto-signs user in (future enhancement)
4. Redirected to dashboard with assigned role

---

## 🔧 Backend Implementation

### New Files Created

**CardAuthController.java**
- Location: `backend/src/main/java/com/makerspace/backend/controller/CardAuthController.java`
- Endpoints:
  - `GET /api/v1/auth/card/check-by-uid` - Check card status
  - `POST /api/v1/auth/card/verify-otp` - Verify OTP and create user
  - `POST /api/v1/auth/card/activate` - Activate card after OTP

### Modified Files

**CardService.java**
- Added methods:
  - `verifyOtp(String otp, String email)` - Verify admin-sent OTP
  - `activateCardAfterOtp(String cardUid, Long userId)` - Activate card
  - `assignRole(Long cardId, AppRole role)` - Assign role to card
  - `deleteUnregisteredCard(Long cardId)` - Delete card
  - `getUnregisteredCardByUid(String uid)` - Lookup card

**SecurityConfig.java**
- Added security rules for card endpoints:
  ```java
  .requestMatchers(GET, "/api/v1/auth/card/check-by-uid").permitAll()
  .requestMatchers(POST, "/api/v1/auth/card/verify-otp").permitAll()
  .requestMatchers(POST, "/api/v1/auth/card/activate").authenticated()
  ```

**EmailService.java** & **SmtpEmailService.java**
- Added: `sendCardActivationOtp(String email, String code)` - Send OTP email

---

## 🎨 Frontend Implementation

### New Files Created

**CardSignInForm.jsx**
- Location: `frontend/src/components/CardSignInForm.jsx`
- Handles:
  - Card tap detection (simulated + real reader)
  - Email display from card assignment
  - OTP input and verification
  - Card activation flow
  - Error handling and user guidance

### Modified Files

**SignIn.jsx**
- Added three tabs:
  - OAuth (existing)
  - Email (existing)
  - Card (new)

**App.css**
- Added styles:
  - `.signin-tabs` - Tab navigation
  - `.card-signin-form` - Card form container
  - `.card-info` - Email display
  - `.card-demo-section` - Demo input
  - `.card-info-box` - Info box
  - `.error-message` - Error styling
  - `.field-hint` - Helper text

---

## 🧪 Testing the Implementation

### Test 1: Check Card Endpoint

```bash
curl -X GET "http://localhost:8080/api/v1/auth/card/check-by-uid?uid=85F181D4"
```

Expected response for unregistered card:
```json
{
  "found": false,
  "message": "Card not found or already registered"
}
```

Expected response for assigned card:
```json
{
  "found": true,
  "email": "user@example.com",
  "status": "VERIFIED",
  "needsOtp": true,
  "otpGenerated": true
}
```

### Test 2: Verify OTP Endpoint

```bash
curl -X POST "http://localhost:8080/api/v1/auth/card/verify-otp" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "otp": "123456"
  }'
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
  "timestamp": "2026-10-25T17:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired OTP",
  "path": "/api/v1/auth/card/verify-otp"
}
```

### Test 3: Activate Card Endpoint

```bash
# First get JWT token from verify-otp
TOKEN="eyJhbGc..."

curl -X POST "http://localhost:8080/api/v1/auth/card/activate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "cardUid": "85F181D4"
  }'
```

Success response:
```json
{
  "success": "true",
  "message": "Card activated with MEMBER access",
  "role": "MEMBER"
}
```

---

## 📊 Database Schema

### UnregisteredCard Table (V17 & V18 Migrations)

```sql
CREATE TABLE unregistered_cards (
  id BIGSERIAL PRIMARY KEY,
  card_uid VARCHAR(255) NOT NULL UNIQUE,
  status VARCHAR(50),                    -- PENDING, ASSIGNED, ROLE_ASSIGNED, VERIFIED, ACTIVATED
  assigned_to_email VARCHAR(255),
  assigned_role VARCHAR(50),             -- MEMBER, STAFF, ADMIN
  otp_code VARCHAR(6),
  otp_generated_at TIMESTAMP,
  first_tap_at TIMESTAMP,
  last_tap_at TIMESTAMP,
  tap_count INTEGER DEFAULT 0,
  device_id VARCHAR(255),
  role_assigned_at TIMESTAMP,
  activated_by_user_id BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 🔐 Security Considerations

1. **OTP Expiration**: 1 hour TTL
2. **OTP Validity**: Single-use only
3. **JWT Token**: Issued after successful OTP verification
4. **Authorization**: Card activation requires authenticated JWT
5. **Card UID**: Case-sensitive, whitespace trimmed
6. **Email**: Case-insensitive, lowercase for consistency

---

## 🚀 Deployment Checklist

- [x] Backend CardAuthController implemented and compiled
- [x] Security rules configured for card endpoints
- [x] Frontend CardSignInForm component created
- [x] SignIn page updated with Card tab
- [x] CSS styling added for card signin
- [x] Database migrations V17 & V18 applied
- [x] Email service integration ready
- [ ] Real NFC card reader integrated
- [ ] Email service tested with actual SMTP
- [ ] Security review completed
- [ ] End-to-end testing performed
- [ ] Production deployment

---

## 🐛 Common Issues & Solutions

### Issue: POST endpoints return 404

**Solution**: Add endpoints to SecurityConfig:
```java
.requestMatchers(POST, "/api/v1/auth/card/verify-otp").permitAll()
.requestMatchers(POST, "/api/v1/auth/card/activate").authenticated()
```

### Issue: OTP email not received

**Solution**: 
1. Check Redis is running: `redis-cli ping`
2. Verify SMTP config in `application.properties`
3. Check backend logs for email errors
4. Use StubEmailService for testing

### Issue: Card not found in check-by-uid

**Solution**:
1. Verify card exists in database: 
   ```sql
   SELECT * FROM unregistered_cards WHERE card_uid = 'CARD_UID';
   ```
2. Check card status - must be VERIFIED or higher
3. Ensure card UID matches exactly (case-sensitive)

### Issue: JWT token not working for activate endpoint

**Solution**:
1. Verify token was issued by verify-otp endpoint
2. Check token hasn't expired (1 hour TTL)
3. Ensure Authorization header format: `Bearer TOKEN`

---

## 📞 Support

For issues or questions:
1. Check backend logs: `/tmp/backend-final.log`
2. Check frontend console: Browser DevTools
3. Use database queries for state verification
4. Review test cases in `CARD_SIGNIN_TESTING_GUIDE.md`

---

## 🎉 Next Steps

1. **Real Card Reader Integration**: Replace demo input with actual NFC reader
2. **Email Service Verification**: Test with actual email service
3. **Performance Testing**: Load test with multiple concurrent cards
4. **Security Audit**: Review authentication/authorization
5. **User Acceptance Testing**: Test with real users and cards
6. **Analytics & Monitoring**: Track activation success rates
7. **Auto Sign-In**: Implement automatic sign-in for registered cards

---

**Status**: ✅ READY FOR TESTING  
**Backend**: Running on port 8080  
**Frontend**: Running on port 5173  
**Server Status**: Fully operational
