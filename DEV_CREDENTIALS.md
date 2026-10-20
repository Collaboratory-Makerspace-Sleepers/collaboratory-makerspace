# Development Credentials & Setup

## Dev Admin Account

**Email:** `Jcs7350@g.rit.edu`
**OTP Code:** `123456` (hardcoded for all emails in dev mode)
**Role:** `ADMIN` (full administrative access)

### How to Use

1. Frontend: Navigate to the login page
2. Enter email: `Jcs7350@g.rit.edu`
3. Click "Send Code"
4. Enter OTP code: `123456`
5. Click "Continue"
6. You will be logged in as an admin user

### Backend Configuration

The backend is running with the following environment variable:
```bash
APP_OTP_DEV_CODE=123456
```

This allows any email to use `123456` as a valid OTP code for development/testing.

### Database Details

- **User ID:** 3
- **Email:** Jcs7350@g.rit.edu
- **Status:** ACTIVE
- **Roles:** ADMIN

### Services Running

| Service | URL | Port |
|---------|-----|------|
| Frontend | http://localhost:5173 | 5173 |
| Backend | http://localhost:8080 | 8080 |
| PostgreSQL | localhost | 5433/5434 |
| Redis | localhost | 6379 |
| pgAdmin | http://localhost:5050 | 5050 |

### Redis Configuration

Redis is running in Docker and is required for:
- OTP code storage (10-minute TTL)
- Session/cache management
- Rate limiting (60-second cooldown between OTP requests)

### Stripe Configuration

The Day Pass product in Stripe is currently **archived/inactive**. To test checkout:
1. Go to https://dashboard.stripe.com/test/products
2. Find product ID: `prod_VRi5dI84EVAPGL` (DAY_PASS)
3. Reactivate the product and its price
4. Try the checkout flow again

Alternatively, create a new test product and update the database:
```sql
UPDATE membership_plan 
SET stripe_price_id = 'price_new_id', stripe_product_id = 'prod_new_id' 
WHERE code = 'DAY_PASS';
```
