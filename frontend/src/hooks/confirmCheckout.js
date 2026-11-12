export async function confirmCheckout(authFetch, type, sessionId) {
  const url = sessionId
    ? "/api/v1/billing/checkout-session/confirm"
    : type === "membership"
      ? "/api/v1/billing/me/subscription/sync"
      : "/api/v1/billing/me/payments/sync";
  if (type === "setup" && !sessionId) {
    throw new Error("Cannot verify this older card setup link. Check Payment Methods before adding a card again.");
  }
  const response = await authFetch(url, {
    method: "POST",
    body: sessionId ? JSON.stringify({ sessionId }) : undefined,
    signal: AbortSignal.timeout(30000),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.message || body.detail
      || "Could not verify payment. Do not pay again; retry confirmation.");
  }
}
