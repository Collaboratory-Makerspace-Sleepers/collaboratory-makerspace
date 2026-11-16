export function dollarsToCents(value) {
  const text = String(value).trim().replace(/^\$/, "").replaceAll(",", "");
  if (!/^\d+(?:\.\d{0,2})?$/.test(text)) {
    throw new Error("Enter a non-negative dollar amount with at most two decimal places.");
  }
  const [whole, fraction = ""] = text.split(".");
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (!Number.isSafeInteger(cents) || cents > 2147483647) {
    throw new Error("Dollar amount is too large.");
  }
  return cents;
}

export function formatDollars(cents) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(cents / 100);
}

export function equipmentRate(item) {
  return item.resolvedHourlyRateCents ?? item.hourlyRateCents;
}

export function hourlyPrice(item) {
  const cents = equipmentRate(item);
  return Number.isInteger(cents) ? (cents === 0 ? "Free" : `${formatDollars(cents)}/hr`) : "Price unavailable";
}

export function sortNewest(items, order = "newest") {
  return [...items].sort((a, b) => {
    const difference = new Date(b.createdAt || 0) - new Date(a.createdAt || 0)
      || b.id - a.id;
    return order === "oldest" ? -difference : difference;
  });
}
