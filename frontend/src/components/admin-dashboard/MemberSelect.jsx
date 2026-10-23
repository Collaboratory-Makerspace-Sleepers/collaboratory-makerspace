import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

/** Search-as-you-type member picker backed by /hardware/cards/members. */
export default function MemberSelect({ value, onChange }) {
  const { authFetch } = useAuth();
  const [query, setQuery] = useState("");
  const [members, setMembers] = useState([]);

  useEffect(() => {
    const timer = setTimeout(async () => {
      try {
        const res = await authFetch(`/api/v1/hardware/cards/members?q=${encodeURIComponent(query)}`);
        if (res.ok) setMembers(await res.json());
      } catch {
        // keep the previous list
      }
    }, 250);
    return () => clearTimeout(timer);
  }, [authFetch, query]);

  return (
    <>
      <input
        placeholder="Search member name or email"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">Select member…</option>
        {members.map((m) => (
          <option key={m.id} value={m.id}>{m.name} ({m.email})</option>
        ))}
      </select>
    </>
  );
}
