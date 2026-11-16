import assert from "node:assert/strict";
import test from "node:test";
import { dollarsToCents, formatDollars, equipmentRate, hourlyPrice, sortNewest } from "./catalog.js";

test("dollar input stores exact integer cents and formats two decimal places", () => {
  for (const [input, cents] of [["0", 0], ["7", 700], ["7.5", 750], ["19.99", 1999],
    ["$1,250.25", 125025], [" 0.29 ", 29]]) {
    assert.equal(dollarsToCents(input), cents);
    assert.equal(dollarsToCents(formatDollars(cents)), cents);
  }
  assert.equal(formatDollars(750), "$7.50");
});

test("invalid or excessive dollar amounts are rejected rather than saved as free", () => {
  for (const input of ["", "-1", "abc", "1.999", "Infinity", "1e2", "21474836.48"]) {
    assert.throws(() => dollarsToCents(input));
  }
});

test("equipment prices use database values and resolved free-access rates without rounding", () => {
  assert.equal(hourlyPrice({ hourlyRateCents: 725 }), "$7.25/hr");
  assert.equal(hourlyPrice({ hourlyRateCents: 725, resolvedHourlyRateCents: 0 }), "Free");
  assert.equal(equipmentRate({ hourlyRateCents: 1000, resolvedHourlyRateCents: 725 }), 725);
  assert.equal(hourlyPrice({}), "Price unavailable");
});

test("newest defaults to creation time, not booking/session start time, and does not mutate", () => {
  const items = [
    { id: 1, createdAt: "2026-11-01T10:00:00Z", startTime: "2026-12-30T10:00:00Z" },
    { id: 2, createdAt: "2026-11-02T10:00:00Z", startTime: "2026-11-20T10:00:00Z" },
    { id: 3, createdAt: "2026-11-02T10:00:00Z", startTime: "2026-11-20T10:00:00Z" },
  ];
  assert.deepEqual(sortNewest(items).map((item) => item.id), [3, 2, 1]);
  assert.deepEqual(sortNewest(items, "oldest").map((item) => item.id), [1, 2, 3]);
  assert.deepEqual(items.map((item) => item.id), [1, 2, 3]);
});
