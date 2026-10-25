# Unregistered Card Registration Flow

## Overview

This feature allows new access cards to be registered in the system through a self-service flow:

1. **New card taps** → Card reader sends UID to backend
2. **Admin dashboard shows new card** → Admin sees it in "New Cards" tab
3. **Admin assigns to email** → Admin enters user's email address
4. **Admin sends OTP code** → Email is sent with 6-digit code
5. **User signs in with OTP** → User visits sign-in page, enters OTP
6. **User taps card again** → Card is now active and grants access

## Architecture

### Database
- **Table:** `unregistered_cards` - Tracks all unregistered card taps
- **Fields:**
  - `card_uid` - Unique card identifier
  - `status` - PENDING, ASSIGNED, VERIFIED, ACTIVATED, REJECTED
  - `assigned_to_email` - Admin assigns card to this email
  - `otp_code` - 6-digit code generated for OTP verification
  - `otp_verified` - Boolean flag when user verifies the OTP
  - `scan_count` - Number of times card has been tapped

### Backend Components

#### Models
- **UnregisteredCard** (`model/UnregisteredCard.java`) - JPA entity for unregistered cards table

#### Repositories
- **UnregisteredCardRepository** (`repository/UnregisteredCardRepository.java`) - Database queries

#### Services
- **CardService** (`services/CardService.java`) - Handles:
  - `scan()` - Modified to track new cards in `unregistered_cards` table
  - `listUnregisteredCards()` - List all pending/assigned cards
  - `assignToEmail()` - Admin assigns card to email
  - `generateOtp()` - Generate and return OTP code
  - `verifyOtp()` - Verify user's OTP entry
  - `activateCardAfterOtp()` - Create MemberCard after OTP verification

#### Controllers
- **UnregisteredCardController** (`controller/UnregisteredCardController.java`) - Admin endpoints:
  - `GET /api/v1/hardware/unregistered-cards` - List unregistered cards
  - `POST /api/v1/hardware/unregistered-cards/{id}/assign` - Assign to email
  - `POST /api/v1/hardware/unregistered-cards/{id}/send-otp` - Send OTP email

- **CardController** (`controller/CardController.java`) - Modified:
  - `POST /api/v1/hardware/card-scan` - Now tracks unregistered cards

### Frontend Components

#### UnregisteredCardsManager
- **Path:** `frontend/src/components/admin-dashboard/UnregisteredCardsManager.jsx`
- **Features:**
  - Auto-refresh every 10 seconds to see new taps
  - Assign email to unregistered card
  - Send OTP to user's email
  - Shows card status, scan count, device info

#### HardwareCheckIns  
- **Updated:** Added "New Cards" tab as first tab
- Renders UnregisteredCardsManager component

## Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ Card Reader taps new card UID (e.g., ABCD1234)              │
└────────────────────────┬────────────────────────────────────┘
                         │ POST /api/v1/hardware/card-scan
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ CardService.scan() finds no MemberCard                      │
│ → Creates UnregisteredCard with status=PENDING              │
│ → Returns "Card not registered - awaiting admin assignment" │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ ADMIN: Opens Hardware Access → New Cards tab                │
│ Sees unregistered card with scan_count=1                    │
└────────────────────────┬────────────────────────────────────┘
                         │ ADMIN ACTION: Assign Email
                         │ POST /api/v1/hardware/unregistered-cards/{id}/assign
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ UnregisteredCard.status = ASSIGNED                          │
│ UnregisteredCard.assigned_to_email = "user@example.com"     │
└────────────────────────┬────────────────────────────────────┘
                         │ ADMIN ACTION: Send OTP
                         │ POST /api/v1/hardware/unregistered-cards/{id}/send-otp
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ CardService.generateOtp() generates 6-digit code            │
│ EmailService sends OTP to assigned_to_email                 │
│ UnregisteredCard.otp_code = code (expires in 1 hour)        │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ USER: Receives email with OTP and sign-in link              │
│ User signs in at /signin with email + OTP                   │
│ Backend verifies OTP via CardService.verifyOtp()            │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ JWT issued: User is now authenticated                       │
│ UnregisteredCard.otp_verified = true                        │
│ UnregisteredCard.status = VERIFIED                          │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ USER: Taps card again at reader                             │
│ POST /api/v1/hardware/card-scan with same UID               │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ CardService.scan() still doesn't find MemberCard            │
│ But on client side: User is authenticated (JWT in cookie)   │
│ Frontend calls CardService.activateCardAfterOtp()           │
└────────────────────────┬────────────────────────────────────┘
                         │ POST /api/v1/hardware/unregistered-cards/activate-after-tap
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ CardService.activateCardAfterOtp():                         │
│ 1. Create new MemberCard with cardUid + userId              │
│ 2. Set active=true                                          │
│ 3. Update UnregisteredCard.status = ACTIVATED               │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ NEXT TAP: Card is now registered as MemberCard              │
│ Access is granted based on membership & training            │
└─────────────────────────────────────────────────────────────┘
```

## API Endpoints

### Admin Endpoints (requires MANAGE_EQUIPMENT permission)

#### GET /api/v1/hardware/unregistered-cards
List all pending and assigned unregistered cards.

**Response:**
```json
[
  {
    "id": 1,
    "cardUid": "ABCD1234",
    "status": "PENDING",
    "assignedToEmail": null,
    "assignedAt": null,
    "otpGenerated": false,
    "otpVerified": false,
    "deviceId": "reader-1",
    "scanCount": 1,
    "firstScannedAt": "2026-10-25T13:15:00Z",
    "lastScannedAt": "2026-10-25T13:15:00Z"
  }
]
```

#### POST /api/v1/hardware/unregistered-cards/{id}/assign
Assign an unregistered card to an email address.

**Request:**
```json
{
  "email": "user@example.com"
}
```

**Response:** Updated UnregisteredCard object

#### POST /api/v1/hardware/unregistered-cards/{id}/send-otp
Generate OTP and send to the assigned email.

**Response:** Updated UnregisteredCard object with OTP details

### User Endpoints (for future frontend integration)

#### POST /api/v1/auth/verify-card-otp
Verify OTP for card activation. Called during sign-in when user enters OTP.

**Request:**
```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

**Response:** JWT token (same as regular OTP flow)

#### POST /api/v1/hardware/unregistered-cards/activate-after-tap
Called after successful card tap when user is already authenticated.

**Request:**
```json
{
  "cardUid": "ABCD1234"
}
```

**Response:** Newly created MemberCard object

## Email Templates

### Card Activation OTP Email
Subject: "Activate your Collaboratory access card"

Body includes:
- Card UID being assigned
- 6-digit OTP code
- Link to sign-in page
- Instructions to tap card after sign-in
- Code expiration time (1 hour)

## Status Transitions

```
PENDING → ASSIGNED → VERIFIED → ACTIVATED
                  ↘ REJECTED (if admin rejects)
```

- **PENDING** - New card first tapped, awaiting admin action
- **ASSIGNED** - Admin assigned card to email
- **VERIFIED** - User verified OTP and is now authenticated
- **ACTIVATED** - Card tapped again and MemberCard created
- **REJECTED** - Admin rejected card (not yet implemented)

## Security Considerations

1. **OTP Expiration** - Code valid for 1 hour only
2. **Email Verification** - Assignment proves email ownership
3. **Authentication Required** - Card activation requires user JWT token
4. **Rate Limiting** - Consider adding rate limits on OTP sends (future)
5. **Device ID Tracking** - Unregistered cards track which device first scanned them

## Future Enhancements

1. **Bulk card import** - Admin upload CSV of card UIDs for batch assignment
2. **Card expiration** - Set card active until specific date
3. **Multiple cards per user** - Support users having backup cards
4. **Card rejection** - Admin reject cards without assignment
5. **Scan history** - Detailed log of all scans for debugging
6. **Email notifications** - Notify admin when new card is tapped
