# 🚀 Quick Start Guide - Card-Based Sign-In System

## ✅ Systems Status
- Backend: Running on http://localhost:8080 ✓
- Frontend: Running on http://localhost:5173 ✓
- Database: PostgreSQL ready ✓

## 📋 3-Step Testing Workflow

### Step 1: Open Admin Dashboard
```
1. Go to http://localhost:5173
2. Log in with admin credentials
3. Navigate to Hardware > New Cards section
```

### Step 2: Set Up Test Card
```
1. Look for card UID: 85F181D4 (or any tapped card)
2. Click "Assign Email" → Enter: testuser@makerspace.local
3. Click "Assign Role" → Select: MEMBER
4. Click "Send OTP" → Check console for OTP code (or email)
```

**Note**: OTP appears in backend logs. Look for:
```
[CardService] Generated OTP: 123456 for email: testuser@makerspace.local
```

### Step 3: User Sign-In with Card
```
1. Open new browser tab: http://localhost:5173
2. Click "Sign In"
3. Click "Card" tab
4. Enter Card UID: 85F181D4 (or use physical card reader)
5. Click "Activate Card & Sign In"
6. Form updates showing:
   - Email: testuser@makerspace.local
   - OTP input field
7. Enter OTP code from Step 2
8. Click "Activate Card & Sign In"
9. Redirected to MEMBER dashboard ✓
```

---

## 🔍 What Happens Behind the Scenes

### Admin Actions (Backend)
```
POST /api/v1/admin/hardware/unregistered-cards/{id}/assign-email
POST /api/v1/admin/hardware/unregistered-cards/{id}/assign-role
POST /api/v1/admin/hardware/unregistered-cards/{id}/send-otp
DELETE /api/v1/admin/hardware/unregistered-cards/{id}
```

### User Actions (Frontend)
```
1. Card Tap
   GET /api/v1/auth/card/check-by-uid?uid=85F181D4
   Response: { found: true, email: "testuser@makerspace.local", status: "VERIFIED" }

2. OTP Verification
   POST /api/v1/auth/card/verify-otp
   Body: { email: "testuser@makerspace.local", otp: "123456" }
   Response: { token: "eyJhbG..." }

3. Card Activation
   POST /api/v1/auth/card/activate
   Header: Authorization: Bearer eyJhbG...
   Response: { success: true, role: "MEMBER" }

4. Redirect to Dashboard
   Location: /dashboard?role=MEMBER
```

---

## 🧪 Testing Scenarios

### Scenario 1: First-Time Card Registration
**Status**: ✅ Ready to test
1. Admin assigns card → email → role → OTP
2. User taps card → sees email → enters OTP → signs in

### Scenario 2: Multiple Roles
**Status**: ✅ Ready to test
1. Create separate cards for:
   - Card 1 → MEMBER role
   - Card 2 → STAFF role
   - Card 3 → ADMIN role
2. Verify each shows appropriate dashboard

### Scenario 3: Expired OTP
**Status**: ✅ Ready to test
1. Generate OTP
2. Wait 1 hour (or modify `OTP_EXPIRATION_MINUTES` for testing)
3. Try to sign in → should fail with "OTP expired"

### Scenario 4: Wrong OTP Code
**Status**: ✅ Ready to test
1. Generate OTP (e.g., 123456)
2. Enter wrong code (e.g., 654321)
3. Should fail with "Invalid OTP"

### Scenario 5: Unregistered Card
**Status**: ✅ Ready to test
1. Tap card that hasn't been assigned
2. Shows: "Card not found - contact admin"

---

## 📱 Frontend UI Walkthrough

### Sign In Page (http://localhost:5173/signin)

**Three Tabs:**
1. **OAuth** (Google, Microsoft, Apple)
   - Standard OAuth login
   - Existing users only

2. **Email** (Email + Code)
   - Enter email
   - Receive OTP via email
   - Enter code
   - Sign in

3. **Card** (NEW - For this system) ✨
   - Tap card reader or enter UID
   - If unregistered → contact admin message
   - If registered → shows email + OTP input
   - Enter admin-sent OTP
   - Sign in to role dashboard

### Admin Dashboard (Hardware > New Cards)

**Table Columns:**
- Card UID (85F181D4)
- Status (PENDING → ASSIGNED → ROLE_ASSIGNED → VERIFIED → ACTIVATED)
- Assigned Email (testuser@makerspace.local)
- Assigned Role (MEMBER / STAFF / ADMIN)
- First Tap (10/25/2026, 1:24:04 PM)
- Last Tap (10/25/2026, 1:24:04 PM)
- Taps (1)
- Device (reader-1)
- Actions (3 buttons)

**Action Buttons:**
- 🔗 Assign Email
- 👤 Assign Role
- 📧 Send OTP
- 🗑️ Delete & Reset

---

## 🐛 Troubleshooting

### Issue: "The server could not send a code"
**Cause**: Email service not configured
**Solution**: 
- Check backend logs for error
- Configure SMTP in `application.properties`:
  ```properties
  spring.mail.host=smtp.gmail.com
  spring.mail.port=587
  spring.mail.username=your-email@gmail.com
  spring.mail.password=your-app-password
  spring.mail.properties.mail.smtp.auth=true
  spring.mail.properties.mail.smtp.starttls.enable=true
  ```
- Or view OTP in backend logs for now

### Issue: POST /api/v1/auth/card/verify-otp returns 404
**Cause**: No card found with that OTP
**Solution**: 
- First, create card via admin dashboard
- Assign email and send OTP
- Then user can sign in

### Issue: Card reader not responding
**Cause**: Physical reader not connected/configured
**Solution**:
- Use demo input field (enters manually)
- Or integrate NFC library (see API documentation)

### Issue: Redirected to blank dashboard
**Cause**: JWT token not properly set
**Solution**:
- Check browser console for errors
- Verify token in Network tab (Authorization header)
- Check backend logs

---

## 📊 Database Verification

### Check Tables
```sql
-- Connect to database
psql -U postgres -d collaboratory

-- View unregistered cards
SELECT card_uid, status, assigned_to_email, assigned_role, otp_code, otp_expires_at 
FROM unregistered_cards 
ORDER BY created_at DESC;

-- View user accounts created
SELECT id, email, role, created_at 
FROM users 
WHERE email LIKE '%makerspace%' 
ORDER BY created_at DESC;
```

---

## 🔐 Security Notes

✅ **What's Secured:**
- OTP codes are 6-digit random values
- Codes expire after 1 hour
- Cards can only be assigned by admins
- JWT tokens required for card activation
- Email verification prevents tampering

⚠️ **Before Production:**
- Configure real SMTP server
- Enable HTTPS/TLS
- Use environment variables for secrets
- Implement rate limiting on OTP attempts
- Add IP whitelisting for admin endpoints
- Enable database encryption at rest

---

## 📈 Monitoring

### Backend Logs
```bash
# Terminal running backend
# Look for:
# - [CardAuthController] endpoint calls
# - [CardService] OTP generation
# - [EmailService] email send results
```

### Frontend Logs
```
# Browser DevTools Console (F12)
# Look for:
# - CardSignInForm render logs
# - API call responses
# - Token storage in localStorage
```

### Database Logs
```
# Check PostgreSQL activity
psql -U postgres -d collaboratory -c "SELECT * FROM pg_stat_statements LIMIT 10;"
```

---

## 🎯 Next Steps

### For Testing (This Week)
- [ ] Test Scenario 1: First-time registration
- [ ] Test Scenario 2: Multiple roles
- [ ] Test card reader device integration
- [ ] Verify email delivery

### For Deployment (This Month)
- [ ] Configure production SMTP
- [ ] Set up SSL certificates
- [ ] Enable database backups
- [ ] Configure monitoring/logging
- [ ] Security audit

### For Enhancement (Future)
- [ ] Auto sign-in for existing users
- [ ] Card reactivation workflow
- [ ] Bulk card import
- [ ] Usage analytics dashboard
- [ ] Mobile app support

---

## 📞 Quick Reference

| Action | URL | Method | Note |
|--------|-----|--------|------|
| Check Card | `/api/v1/auth/card/check-by-uid?uid=...` | GET | Public |
| Verify OTP | `/api/v1/auth/card/verify-otp` | POST | Public |
| Activate Card | `/api/v1/auth/card/activate` | POST | Auth required |
| Admin: View Cards | `/api/v1/admin/hardware/unregistered-cards` | GET | Admin only |
| Admin: Assign Email | `/api/v1/admin/hardware/unregistered-cards/{id}/assign-email` | POST | Admin only |
| Admin: Assign Role | `/api/v1/admin/hardware/unregistered-cards/{id}/assign-role` | POST | Admin only |
| Admin: Send OTP | `/api/v1/admin/hardware/unregistered-cards/{id}/send-otp` | POST | Admin only |
| Admin: Delete Card | `/api/v1/admin/hardware/unregistered-cards/{id}` | DELETE | Admin only |

---

## ✨ You're All Set!

Everything is ready. Pick a test scenario above and start testing. The system will create users automatically when OTP is verified.

Good luck! 🚀
