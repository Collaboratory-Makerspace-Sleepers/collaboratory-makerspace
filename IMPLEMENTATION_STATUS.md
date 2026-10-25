# Card Registration Implementation Status

## Completed
✅ Backend CardAuthController created with:
  - POST /api/v1/auth/card/verify-otp - Verify admin-sent OTP
  - POST /api/v1/auth/card/activate - Activate card after OTP verification
  - GET /api/v1/auth/card/check-by-uid - Check if card needs OTP

✅ CardService enhancements:
  - verifyOtp(String otp, String email) - Verify admin-sent OTP code
  - activateCardAfterOtp() - Activate card and assign role to user
  - assignRole() - Assign role to unregistered card
  - deleteUnregisteredCard() - Delete card for testing
  - getUnregisteredCardByUid() - Lookup card by UID

✅ Admin Dashboard:
  - View new unregistered cards in "New Cards" tab
  - Assign email to card
  - Assign role (MEMBER/STAFF/ADMIN)
  - Send OTP code to user email
  - Delete card for testing

✅ Database:
  - V17 - UnregisteredCard table with OTP fields
  - V18 - Add assignedRole and roleAssignedAt columns

✅ Backend compilation & startup
  - No compilation errors
  - Server running on port 8080

## In Progress
🔄 Frontend startup
  - Starting Vite dev server on port 5173
  - Waiting for completion

## Not Yet Started
❌ Frontend sign-in flow modifications:
  - Create card-first sign-in page
  - Prompt user to tap card before entering credentials
  - Show email (read-only) from unregistered card
  - Accept OTP code entered by user
  - Route to /api/v1/auth/card/verify-otp
  - Automatically call /api/v1/auth/card/activate after OTP verification

❌ Card reader integration:
  - Integrate NFC card reader with frontend
  - Trigger card tap detection before login
  - Prevent access until valid card is tapped
  - Store card UID in session for OTP verification

❌ Testing:
  - Test complete end-to-end flow
  - Verify role-based dashboard access
  - Test delete/reset functionality
  - Test with different roles (MEMBER/STAFF/ADMIN)

## Flow Overview
1. Admin creates unregistered cards via card reader (PENDING status)
2. Admin assigns email → role → sends OTP (via admin dashboard)
3. User receives email with OTP code
4. User taps card → Sees sign-in form with email pre-filled
5. User enters OTP code from email
6. Backend calls verifyCardOtp() → Creates/activates user → Issues JWT
7. Frontend automatically calls activateCard() with card UID
8. Card moves to ACTIVATED status and user assigned the admin-selected role
9. User sees role-appropriate dashboard (MEMBER/STAFF/ADMIN)

## Key Differences from Regular OTP Flow
- Regular OTP: /api/v1/auth/otp/send → /api/v1/auth/otp/verify
- Card OTP: Admin sends via dashboard → verifyCardOtp() → activateCard()
- Card must be tapped BEFORE sign-in (not after)
- Only admin-sent OTP code is valid (no generation on demand)
- Role assignment happens during card activation
