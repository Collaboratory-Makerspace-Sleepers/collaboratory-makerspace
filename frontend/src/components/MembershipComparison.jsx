import { useEffect, useState } from "react";

function formatPrice(amountCents, billingInterval) {
  const dollars = (amountCents / 100).toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 0,
  });
  return billingInterval ? `${dollars}/${billingInterval}` : dollars;
}

const BENEFIT_ROWS = [
  { label: "Main Workshop Access", values: ["✓", "✓", "✓", "✓", "✓", "✓"] },
  { label: "Equipment Rental",     values: ["✓", "✓", "✓", "Free", "Free", "Free"] },
  { label: "Class Bookings",       values: ["✓", "✓", "✓", "50% off", "50% off", "50% off"] },
  { label: "Private Studio",       values: ["—", "—", "—", "Small", "Medium", "Large"] },
];

export default function MembershipComparison() {
  const [plans, setPlans] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    fetch("/api/v1/billing/plans")
      .then((res) => { if (!res.ok) throw new Error(); return res.json(); })
      .then(setPlans)
      .catch(() => setError("Could not load pricing. Please try again later."));
  }, []);

  if (error) return <p className="pricing-error">{error}</p>;
  if (!plans.length) return null;

  return (
    <div className="comparison-wrapper">
      <table className="comparison-table">
        <thead>
          <tr>
            <th className="feature-col"></th>
            {plans.map((plan) => (
              <th key={plan.code}>
                <div className="plan-name">{plan.displayName}</div>
                <div className="plan-price">{formatPrice(plan.amountCents, plan.billingInterval)}</div>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {BENEFIT_ROWS.map((row) => (
            <tr key={row.label}>
              <td className="feature-label">{row.label}</td>
              {plans.map((plan, i) => (
                <td key={plan.code} className="benefit-cell">
                  {row.values[i] ?? "—"}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
