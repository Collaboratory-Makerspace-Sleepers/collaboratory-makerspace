# Stripe Payment Integration - Complete Setup & Testing Guide

## Overview

This system integrates Stripe payments with equipment rentals. The flow is:
1. User selects equipment and time slot
2. (Optional) User signs waiver via RabbitSign
3. User pays via Stripe checkout
4. After payment, reservation is automatically created
5. Reservation appears in "My Reservations"

---

## Part 1: Get Stripe Sandbox Credentials

### Step 1a: Create Stripe Account (or login)
- Go to https://stripe.com (create account if needed)
- Access test dashboard: https://dashboard.stripe.com/test

### Step 1b: Get API Keys
1. In Stripe dashboard, go to **Developers → API keys**
2. You'll see two keys in TEST mode:
   - **Publishable key**: `pk_test_...` (public, safe to share)
   - **Secret key**: `sk_test_...` (keep this private!)

Example:
```
pk_test_51234567890abcdefghijklmnop
sk_test_98765432109abcdefghijklmnop
```

### Step 1c: Get Webhook Secret
1. Go to **Developers → Webhooks**
2. Click **Add endpoint**
3. Endpoint URL: `http://localhost:8080/api/dev/stripe/webhook`
4. Select events to listen:
   - ✅ `checkout.session.completed`
   - ✅ `customer.subscription.created`
   - ✅ `customer.subscription.updated`
   - ✅ `customer.subscription.deleted`
   - ✅ `payment_intent.succeeded`
5. Click **Add endpoint**
6. Copy the **Signing secret**: `whsec_...`

---

## Part 2: Configure Backend

### Step 2a: Create .env file
In the `backend` folder, create or edit `.env`:

```bash
# Copy from backend/.env.example and update:
STRIPE_SECRET_KEY=sk_test_your_actual_key
STRIPE_WEBHOOK_SECRET=whsec_your_actual_secret
```

### Step 2b: Restart Backend
Stop the running backend and restart:
```bash
# Stop any running mvnw
Ctrl+C in the backend terminal

# Start again
cd backend
./mvnw spring-boot:run
```

Verify startup logs show:
```
[INFO] Tomcat started on port(s): 8080
```

---

## Part 3: Configure Frontend

### Step 3a: Create frontend .env
In the `frontend` folder, create or edit `.env`:

```bash
# Copy from frontend/.env.example and update:
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_your_actual_key
VITE_RABBITSIGN_WAIVER_URL=https://www.rabbitsign.com/template/link/...
```

### Step 3b: Restart Frontend
Stop the running frontend and restart:
```bash
# Stop Vite dev server
Ctrl+C in the frontend terminal

# Restart
cd frontend
npm run dev
```

Verify it shows:
```
VITE v7.x.x  ready in xxx ms
```

---

## Part 4: Create Test Products (Optional)

### Option A: Use Stripe Dashboard
1. Go to **Products** → **All products**
2. Click **Add product**
   - Name: `Equipment Rental - Hourly`
   - Price: `7.00 USD`
   - Billing period: `One-time`
3. Save and copy the **Price ID** (format: `price_...`)

### Option B: Create via Database
If you want to create a custom product:
```sql
-- Connect to local database (or use pgAdmin at http://localhost:5050)
INSERT INTO membership_plan 
  (code, name, description, price, stripe_product_id, stripe_price_id, active, created_at)
VALUES 
  ('EQUIPMENT_HOURLY', 'Equipment Hourly', 'Hourly equipment rental', 7.00, 
   'prod_xxx', 'price_xxx', true, NOW());
```

---

## Part 5: Equipment Configuration

### Enable Equipment Rentals

Update equipment in the database to support rentals and waivers:

```sql
-- Check current equipment
SELECT id, name, require_waiver_for_checkout FROM equipment;

-- Enable waiver requirement for specific equipment
UPDATE equipment 
SET require_waiver_for_checkout = true 
WHERE name LIKE '%Laser%';

-- Or enable for all equipment
UPDATE equipment SET require_waiver_for_checkout = true;
```

---

## Part 6: Test the Complete Flow

### Test Credentials
- **Email**: `Jcs7350@g.rit.edu`
- **OTP**: `123456` (hardcoded for dev)

### Step-by-Step Test

#### 6a: Login
1. Open http://localhost:5173
2. Sign in with email above
3. OTP will be automatically filled (dev mode)
4. Click "Continue"

#### 6b: Navigate to Equipment Booking
1. Click "Dashboard" or "Reserve Equipment"
2. Select an equipment item (e.g., "Laser Cutter")
3. Pick a **future date** (can't book past dates)
4. Select a time slot (9:00 AM, 10:00 AM, etc.)

#### 6c: Sign Waiver (if enabled)
1. Click "Sign Waiver"
2. A RabbitSign popup opens
3. Draw your signature in the box
4. Click "Submit" or "Sign"
5. Wait for confirmation (usually immediate in sandbox)
6. Return to the booking flow

#### 6d: Make Payment
1. Summary shows: Equipment name, date, time, price
2. Click "Pay $7.00" (or calculated amount)
3. **Stripe checkout page opens** with:
   - Email field (auto-filled)
   - Card number field
   - Expiry date field
   - CVC field
4. Enter test card details:
   - **Card**: `4242 4242 4242 4242`
   - **Exp**: `12/25` (or any future date)
   - **CVC**: `123`
5. Click "Pay"

#### 6e: Verify Reservation Created
1. After payment, redirected to success page
2. Click "View my reservations"
3. **Reservation should appear** with:
   - Equipment name
   - Date and time
   - Rental price
   - Status: `CONFIRMED`

---

## Part 7: Test Different Scenarios

### Test Card Numbers (Stripe Test Mode)
- `4242 4242 4242 4242` → Always succeeds
- `4000 0025 0000 3155` → Requires authentication (3D Secure)
- `4000 0000 0000 0002` → Always declined
- `5555 5555 5555 4444` → Mastercard success

### Test Webhook Events (Local)
If you want to test webhooks locally:

```bash
# Install Stripe CLI: https://stripe.com/docs/stripe-cli
stripe login

# Forward events to localhost
stripe listen --forward-to localhost:8080/api/dev/stripe/webhook

# Copy the signing secret and add to .env:
# STRIPE_WEBHOOK_SECRET=whsec_...
```

Then trigger test events:
```bash
stripe trigger checkout.session.completed
```

---

## Part 8: Troubleshooting

### Issue: "Stripe keys not found"
**Symptoms**: Payment button says "Stripe keys not configured"
**Fix**:
- Check `.env` file exists in backend folder
- Verify `STRIPE_SECRET_KEY` is set correctly
- Restart backend: `Ctrl+C` then `./mvnw spring-boot:run`

### Issue: Payment redirects to blank/error page
**Symptoms**: Click "Pay" but nothing happens
**Fix**:
- Check browser console (F12) for errors
- Verify `VITE_STRIPE_PUBLISHABLE_KEY` is set in frontend `.env`
- Verify frontend is restarted after `.env` changes

### Issue: Payment succeeds but reservation doesn't appear
**Symptoms**: Stripe says "Success" but "My Reservations" is empty
**Fix**:
- Wait 2-3 seconds (automatic creation takes time)
- Refresh the page (F5)
- Check backend logs for errors: `tail backend/logs/app.log`

### Issue: Webhook says "Event not received"
**Symptoms**: Stripe CLI shows "Failed to POST to"
**Fix**:
- Verify endpoint URL: `http://localhost:8080/api/dev/stripe/webhook` (not https)
- Check firewall isn't blocking port 8080
- Ensure ngrok/tunnel is NOT used (direct localhost only)

### Issue: Waiver popup doesn't open
**Symptoms**: "Sign Waiver" button clicked but nothing happens
**Fix**:
- Check RabbitSign URL is valid
- Browser console should show popup attempt
- Disable popup blockers for localhost:5173
- Verify `VITE_RABBITSIGN_WAIVER_URL` is set

### Issue: "Signature is invalid" error
**Symptoms**: Waiver submission fails with signature error
**Fix**:
- Make sure you actually drew on the signature pad
- RabbitSign might require specific template setup
- Check RabbitSign template is active and public

---

## Part 9: Next Steps

### After Testing Successfully:
1. Create production Stripe account (separate from sandbox)
2. Get production API keys (starts with `pk_live_`, `sk_live_`)
3. Deploy to production environment
4. Update configuration to use production keys
5. Test with real credit card (Stripe will refund immediately)

### Advanced Features:
- [ ] Save payment methods for future bookings
- [ ] Show past reservation history
- [ ] Cancel reservations and refund payments
- [ ] Email confirmation after booking
- [ ] SMS reminders before reservation time
- [ ] Digital waiver storage/retrieval

---

## Quick Commands

```bash
# Backend - Start
cd backend
./mvnw spring-boot:run

# Frontend - Start  
cd frontend
npm run dev

# Backend - Check if running
curl http://localhost:8080/actuator/health

# Frontend - Check if running
curl http://localhost:5173

# View Docker services
docker-compose -f backend/docker-compose.yml ps

# View pgAdmin (database UI)
# Open http://localhost:5050
# Login: admin@local.com / admin123
```

---

## Support & Resources

- Stripe Docs: https://stripe.com/docs
- Stripe API Reference: https://stripe.com/docs/api
- RabbitSign: https://www.rabbitsign.com
- React Context: https://react.dev/reference/react/useContext
- Spring Boot: https://spring.io/guides/gs/spring-boot/
