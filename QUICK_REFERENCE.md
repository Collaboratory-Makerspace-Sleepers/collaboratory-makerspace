# Quick Reference - NFC Card Sign-In System

## 🎯 TL;DR

**Where is my data?**
→ Admin Dashboard → Hardware Access → **"NEW CARDS"** tab

**What do I do?**
1. Open sign-in form
2. Tap a card
3. Go to admin dashboard "NEW CARDS" tab
4. Assign email + role
5. User signs in with OTP

---

## 📍 Quick Navigation

| What | Where |
|------|-------|
| Sign-In Form | http://localhost:5173/signin |
| Admin Dashboard | http://localhost:5173/admin-dashboard/home |
| Hardware Access | Admin Dashboard → Left sidebar → Hardware Access |
| Unregistered Cards | Hardware Access → **NEW CARDS** tab |
| Live Taps | Hardware Access → CARD TAPS tab |
| Member Cards | Hardware Access → CARDS tab |

---

## 🔄 Card Lifecycle

```
TAP → PENDING → ASSIGNED → ROLE_ASSIGNED → VERIFIED → ACTIVATED
```

| Status | Action | Who |
|--------|--------|-----|
| PENDING | Card tapped at sign-in | System |
| ASSIGNED | Click "Assign Email" | Admin |
| ROLE_ASSIGNED | Click "Assign Role" | Admin |
| VERIFIED | "Send Code" requested | User/Admin |
| ACTIVATED | OTP code entered | User |

---

## 🎬 Complete Test Scenario

### Scenario: First-Time User Sign-In

1. **User Action:**
   - Go to: http://localhost:5173/signin
   - Click: "Card" tab
   - Enter: Card UID (e.g., `NEWCARD123`)
   - Press: Enter

2. **Admin Action:**
   - Go to: http://localhost:5173/admin-dashboard/hardware
   - Click: "NEW CARDS" tab
   - Find: Your card (status: PENDING)
   - Click: "Assign Email" → Enter email → Submit
   - Click: "Assign Role" → Select role → Submit

3. **User Action (Continued):**
   - Go back to sign-in form
   - Enter same card UID
   - See: "Send Code to Email" button
   - Click: Button
   - Check: Email for 6-digit code
   - Enter: Code in form
   - Click: "Sign In"

4. **Result:**
   - ✅ User signed in
   - ✅ Redirected to role dashboard
   - ✅ Card status: ACTIVATED

---

## 🔧 Admin Actions

| Button | Input | Result |
|--------|-------|--------|
| Assign Email | user@example.com | Status → ASSIGNED |
| Assign Role | MEMBER\|STAFF\|ADMIN | Status → ROLE_ASSIGNED |
| Send OTP | — | Status → VERIFIED (OTP sent) |
| Delete | — | Card removed (test reset) |

---

## 📊 Data Storage

| Table | Purpose |
|-------|---------|
| unregistered_cards | New cards (PENDING-ACTIVATED) |
| member_card | Registered active cards |
| users | User accounts |
| card_scans | Audit trail of all taps |

All stored in PostgreSQL database.

---

## 🧪 API Quick Test

```bash
# Check if card exists
curl http://localhost:8080/api/v1/auth/card/check-by-uid?uid=85F181D4

# Simulate card tap (creates UnregisteredCard)
curl -X POST http://localhost:8080/api/v1/hardware/card-scan \
  -H "Content-Type: application/json" \
  -d '{"cardUid":"TESTCARD","deviceId":"sign-in-reader"}'

# Request OTP
curl -X POST http://localhost:8080/api/v1/auth/card/send-otp \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","cardUid":"TESTCARD"}'

# Verify OTP
curl -X POST http://localhost:8080/api/v1/auth/card/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","otp":"123456"}'
```

---

## ✅ System Status Checks

| Component | Check |
|-----------|-------|
| Backend | curl http://localhost:8080/actuator/health |
| Frontend | http://localhost:5173/signin |
| Admin | http://localhost:5173/admin-dashboard/home |
| Database | PostgreSQL running (check status) |

---

## 🐛 Common Issues

| Issue | Solution |
|-------|----------|
| No cards in dashboard | Click "NEW CARDS" tab (not "CARDS") |
| Card doesn't appear | Tap card in sign-in form first |
| Email not received | Check spam, verify config |
| OTP expired | Request new code (1 hour limit) |
| Can't assign | Check you're logged in as admin |

---

## 📁 Key Files

| File | Purpose |
|------|---------|
| CardSignInForm.jsx | User sign-in interface |
| UnregisteredCardsManager.jsx | Admin card management |
| CardAuthController.java | Backend OTP endpoints |
| CardService.java | Card business logic |

---

## 🚀 Getting Started

```
1. Open admin dashboard
   → http://localhost:5173/admin-dashboard/hardware

2. Look for tabs at top
   → NEW CARDS | CARD TAPS | CARDS | WAIVERS

3. Click "NEW CARDS" tab
   → See unregistered cards here

4. Test flow:
   → Go to sign-in
   → Tap card
   → Assign email + role
   → User signs in
```

---

## 📚 Full Documentation

- **ADMIN_DASHBOARD_GUIDE.md** - Detailed admin workflows
- **COMPLETE_TESTING_GUIDE.md** - Full testing procedures
- **UPDATED_CARD_SIGNIN_FLOW.md** - Technical architecture
- **TESTING_NEW_FLOW.md** - Test results and verification

---

**Everything is working! Just navigate to the right place.** ✅
