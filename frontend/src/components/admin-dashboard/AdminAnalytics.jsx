import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import {
  LineChart, Line, BarChart, Bar, AreaChart, Area,
  PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer,
} from "recharts";

const COLORS = ["#6366f1", "#22d3ee", "#f59e0b", "#10b981", "#f43f5e", "#8b5cf6"];

function fmt$(cents) {
  return "$" + (cents / 100).toLocaleString("en-US", { minimumFractionDigits: 2 });
}

function monthLabel(ts) {
  if (!ts) return "";
  const d = new Date(ts);
  return d.toLocaleString("default", { month: "short", year: "2-digit" });
}

function dayLabel(ts) {
  if (!ts) return "";
  const d = new Date(ts);
  return (d.getMonth() + 1) + "/" + d.getDate();
}

export default function AdminAnalytics() {
  const { authFetch } = useAuth();
  const [revenue, setRevenue] = useState(null);
  const [memberships, setMemberships] = useState(null);
  const [equipment, setEquipment] = useState(null);
  const [visits, setVisits] = useState(null);

  useEffect(() => {
    authFetch("/api/v1/admin/analytics/revenue").then(r => r.ok && r.json()).then(d => d && setRevenue(d));
    authFetch("/api/v1/admin/analytics/memberships").then(r => r.ok && r.json()).then(d => d && setMemberships(d));
    authFetch("/api/v1/admin/analytics/equipment").then(r => r.ok && r.json()).then(d => d && setEquipment(d));
    authFetch("/api/v1/admin/analytics/visits").then(r => r.ok && r.json()).then(d => d && setVisits(d));
  }, [authFetch]);

  // Build month-aggregated revenue line data
  const revenueLineData = (() => {
    if (!revenue) return [];
    const map = {};
    for (const row of revenue.monthly) {
      const label = monthLabel(row.month);
      if (!map[label]) map[label] = { month: label };
      map[label][row.kind] = (map[label][row.kind] || 0) + (row.totalCents / 100);
      map[label].total = (map[label].total || 0) + (row.totalCents / 100);
    }
    return Object.values(map);
  })();

  const revenueKinds = [...new Set((revenue?.monthly || []).map(r => r.kind))];

  const membershipMonthData = (memberships?.newByMonth || []).map(r => ({
    month: monthLabel(r.month),
    signups: Number(r.count),
  }));

  const membershipPieData = (memberships?.byPlan || []).map(r => ({
    name: r.displayName || r.code,
    value: Number(r.count),
  }));

  const topEquipmentData = (equipment?.topEquipment || []).map(r => ({
    name: r.name,
    reservations: Number(r.reservations),
    hours: parseFloat(Number(r.hours).toFixed(1)),
  }));

  const hourData = (() => {
    const all = Array.from({ length: 24 }, (_, i) => ({ hour: i, count: 0 }));
    for (const row of (equipment?.byHourOfDay || [])) {
      all[Number(row.hour)].count = Number(row.count);
    }
    return all;
  })();

  const visitsData = (visits?.daily || []).map(r => ({
    day: dayLabel(r.day),
    visits: Number(r.count),
  }));

  return (
    <div className="analytics-page">
      <title>Analytics | The Collaboratory</title>
      <h2 className="analytics-title">Analytics</h2>

      {/* ── Revenue ─────────────────────────────────────────────── */}
      <section className="analytics-section">
        <h3>Revenue</h3>
        <div className="analytics-stat-row">
          <div className="analytics-mini-card">
            <span className="analytics-mini-label">This Month</span>
            <span className="analytics-mini-value">{revenue ? fmt$(revenue.thisMonthCents) : "—"}</span>
          </div>
          <div className="analytics-mini-card">
            <span className="analytics-mini-label">Last Month</span>
            <span className="analytics-mini-value">{revenue ? fmt$(revenue.lastMonthCents) : "—"}</span>
          </div>
        </div>

        <div className="analytics-charts-row">
          <div className="analytics-chart-box">
            <p className="analytics-chart-label">Monthly Total (12 mo)</p>
            <ResponsiveContainer width="100%" height={220}>
              <LineChart data={revenueLineData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                <YAxis tickFormatter={v => "$" + v.toLocaleString()} tick={{ fontSize: 11 }} />
                <Tooltip formatter={v => "$" + Number(v).toFixed(2)} />
                <Line type="monotone" dataKey="total" stroke="#6366f1" dot={false} strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          </div>

          <div className="analytics-chart-box">
            <p className="analytics-chart-label">Revenue by Kind</p>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={revenueLineData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                <YAxis tickFormatter={v => "$" + v.toLocaleString()} tick={{ fontSize: 11 }} />
                <Tooltip formatter={v => "$" + Number(v).toFixed(2)} />
                <Legend />
                {revenueKinds.map((k, i) => (
                  <Bar key={k} dataKey={k} stackId="a" fill={COLORS[i % COLORS.length]} />
                ))}
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </section>

      {/* ── Memberships ─────────────────────────────────────────── */}
      <section className="analytics-section">
        <h3>Memberships</h3>
        <div className="analytics-stat-row">
          <div className="analytics-mini-card">
            <span className="analytics-mini-label">Active Members</span>
            <span className="analytics-mini-value">{memberships ? memberships.totalActive : "—"}</span>
          </div>
        </div>

        <div className="analytics-charts-row">
          <div className="analytics-chart-box">
            <p className="analytics-chart-label">New Signups by Month (12 mo)</p>
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={membershipMonthData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                <Tooltip />
                <Area type="monotone" dataKey="signups" stroke="#22d3ee" fill="#cffafe" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          <div className="analytics-chart-box">
            <p className="analytics-chart-label">Active Members by Plan</p>
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie data={membershipPieData} dataKey="value" nameKey="name"
                  cx="50%" cy="50%" outerRadius={80} label={({ name, percent }) =>
                    `${name} ${(percent * 100).toFixed(0)}%`}>
                  {membershipPieData.map((_, i) => (
                    <Cell key={i} fill={COLORS[i % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>
      </section>

      {/* ── Equipment ───────────────────────────────────────────── */}
      <section className="analytics-section">
        <h3>Equipment Utilization</h3>
        <div className="analytics-charts-row">
          <div className="analytics-chart-box">
            <p className="analytics-chart-label">Top 10 Machines by Reservations</p>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={topEquipmentData} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
                <YAxis type="category" dataKey="name" width={120} tick={{ fontSize: 11 }} />
                <Tooltip />
                <Bar dataKey="reservations" fill="#6366f1" />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <div className="analytics-chart-box">
            <p className="analytics-chart-label">Reservations by Hour of Day (12 mo)</p>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={hourData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="hour" tick={{ fontSize: 11 }} label={{ value: "Hour", position: "insideBottom", offset: -2 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                <Tooltip formatter={(v, _, p) => [v, `${p.payload.hour}:00`]} />
                <Bar dataKey="count" fill="#f59e0b" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </section>

      {/* ── Visits ──────────────────────────────────────────────── */}
      <section className="analytics-section">
        <h3>Facility Visits</h3>
        <div className="analytics-stat-row">
          <div className="analytics-mini-card">
            <span className="analytics-mini-label">Today</span>
            <span className="analytics-mini-value">{visits ? visits.todayCount : "—"}</span>
          </div>
          <div className="analytics-mini-card">
            <span className="analytics-mini-label">This Week</span>
            <span className="analytics-mini-value">{visits ? visits.thisWeekCount : "—"}</span>
          </div>
        </div>

        <div className="analytics-charts-row">
          <div className="analytics-chart-box" style={{ flex: 1 }}>
            <p className="analytics-chart-label">Daily Granted Access (30 days)</p>
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={visitsData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="day" tick={{ fontSize: 11 }} interval={4} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                <Tooltip />
                <Area type="monotone" dataKey="visits" stroke="#10b981" fill="#d1fae5" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      </section>
    </div>
  );
}