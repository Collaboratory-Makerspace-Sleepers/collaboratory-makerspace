# Stripe Sandbox Setup Guide

## Step 1: Get Your Stripe Sandbox Keys

1. Go to https://dashboard.stripe.com/test/apikeys
2. You should see:
   - **Publishable Key**: `pk_test_...` (starts with pk_test)
   - **Secret Key**: `sk_test_...` (starts with sk_test)

3. Go to https://dashboard.stripe.com/test/webhooks
4. Click "Add endpoint"
5. Enter URL: `http://localhost:8080/api/dev/stripe/webhook`
6. Select events:
   - checkout.session.completed
   - customer.subscription.created
   - customer.subscription.updated
   - customer.subscription.deleted
7. Copy the **Signing secret**: `whsec_...`

## Step 2: Update Backend Configuration

Edit `.env` file in the backend folder (create if doesn't exist):

```bash
STRIPE_SECRET_KEY=sk_test_your_key_here
STRIPE_WEBHOOK_SECRET=whsec_your_secret_here
```

Or set environment variables before running:

```bash
export STRIPE_SECRET_KEY=sk_test_your_key_here
export STRIPE_WEBHOOK_SECRET=whsec_your_secret_here
cd backend
./mvnw spring-boot:run
```

## Step 3: Update Frontend Configuration

Create `.env` file in the frontend folder:

```
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_your_key_here
VITE_RABBITSIGN_WAIVER_URL=https://www.rabbitsign.com/template/link/SzNLl1tmVh80eu086cmInx
```

## Step 4: Test the Payment Flow

### Test Card Numbers (Stripe Sandbox)
- **Success**: `4242 4242 4242 4242`
- **Requires Authentication**: `4000 0025 0000 3155`
- **Declined**: `4000 0000 0000 0002`

Expiry: Any future date (e.g., 12/25)
CVC: Any 3 digits

## Step 5: Create a Test Product in Stripe

1. Go to https://dashboard.stripe.com/test/products
2. Click "Add product"
3. Name: `Equipment Rental - Hourly`
4. Price: `$7.00 USD`
5. Billing period: One-time
6. Copy the **Price ID** (`price_...`)

## Step 6: Update Database with Stripe Product

Create a new rental plan or update existing:

```sql
-- Check existing plans
SELECT * FROM membership_plan;

-- Or add a rental plan (if not exists)
INSERT INTO membership_plan (code, name, description, price, stripe_product_id, stripe_price_id, active, created_at)
VALUES ('EQUIPMENT_HOURLY', 'Equipment Rental - Hourly', 'Hourly equipment rental rate', 7.00, 'prod_...', 'price_...', true, NOW())
ON CONFLICT DO NOTHING;
```

## Step 7: Test Waiver + Payment Flow

1. Open http://localhost:5173 (frontend)
2. Sign in with: `Jcs7350@g.rit.edu` / OTP: `123456`
3. Navigate to "Reserve Equipment"
4. Select an equipment item
5. Pick a date and time slot
6. If waiver is required:
   - Click "Sign Waiver"
   - RabbitSign popup opens
   - Sign and submit
   - Wait for waiver approval (simulated)
7. Proceed to payment:
   - Waiver + Payment modal appears
   - Fill in card details (use test card above)
   - Submit payment
8. Check "My Reservations" - should see the confirmed booking

## Step 8: Start Stripe CLI for Local Webhooks (Optional)

If you want to test webhook events locally:

```bash
# Install Stripe CLI: https://stripe.com/docs/stripe-cli
stripe login
stripe listen --forward-to localhost:8080/api/dev/stripe/webhook
```

Copy the webhook signing secret and add to `.env`

## Troubleshooting

### Issue: Stripe keys not loading
- Check `.env` file is in the correct folder
- Restart backend: `./mvnw spring-boot:run`
- Check logs for: `stripe.secret.key` or `STRIPE_SECRET_KEY`

### Issue: Webhook not working
- Verify webhook URL is correct: `http://localhost:8080/api/dev/stripe/webhook`
- Check firewall/router isn't blocking port 8080
- Use Stripe CLI to test events locally

### Issue: Payment shows "Invalid product"
- Verify product ID in database matches Stripe
- Ensure the price is active in Stripe

### Issue: Waiver doesn't show before payment
- Check `waiverRequired` is set to `true` on equipment
- Verify RabbitSign URL is valid
- Check browser console for errors

## File Paths Modified

- Backend config: `backend/src/main/resources/application-local.yml`
- Frontend env: `frontend/.env`
- Backend env: `backend/.env`
