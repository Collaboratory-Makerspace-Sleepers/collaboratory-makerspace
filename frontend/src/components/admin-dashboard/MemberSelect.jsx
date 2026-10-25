import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";

/** Search-as-you-type member picker backed by /hardware/cards/members. */
export default function MemberSelect({ value, onChange, onCreateUser }) {
  const { authFetch } = useAuth();
  const [query, setQuery] = useState("");
  const [members, setMembers] = useState([]);
  const [isOpen, setIsOpen] = useState(false);

  useEffect(() => {
    const timer = setTimeout(async () => {
      if (query.trim()) {
        try {
          const res = await authFetch(`/api/v1/hardware/cards/members?q=${encodeURIComponent(query)}`);
          if (res.ok) setMembers(await res.json());
        } catch {
          // keep the previous list
        }
      } else {
        setMembers([]);
      }
    }, 250);
    return () => clearTimeout(timer);
  }, [authFetch, query]);

  const selectedMember = members.find(m => String(m.id) === String(value));

  return (
    <div className="MemberSelectContainer" style={{ position: "relative" }}>
      <div style={{
        border: "1px solid #ddd",
        borderRadius: "4px",
        padding: "0",
        display: "flex",
        alignItems: "center",
        gap: "0",
      }}>
        <input
          type="text"
          placeholder="Search member by name or email..."
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setIsOpen(true);
          }}
          onFocus={() => setIsOpen(true)}
          style={{
            flex: 1,
            border: "none",
            padding: "8px 12px",
            fontSize: "14px",
            outline: "none",
          }}
        />
        {query && (
          <button
            type="button"
            onClick={() => {
              setQuery("");
              setMembers([]);
              onChange("");
            }}
            style={{
              background: "none",
              border: "none",
              padding: "8px 12px",
              cursor: "pointer",
              fontSize: "16px",
              color: "#999",
            }}
          >
            ✕
          </button>
        )}
      </div>

      {isOpen && (query.trim() || members.length > 0) && (
        <div
          style={{
            position: "absolute",
            top: "100%",
            left: 0,
            right: 0,
            background: "white",
            border: "1px solid #ddd",
            borderTop: "none",
            borderRadius: "0 0 4px 4px",
            boxShadow: "0 4px 6px rgba(0,0,0,0.1)",
            zIndex: 100,
            maxHeight: "250px",
            overflowY: "auto",
          }}
        >
          {members.length === 0 && query.trim() && (
            <div style={{ padding: "12px" }}>
              <div style={{ color: "#666", fontSize: "13px", marginBottom: "8px" }}>
                No members found for "{query}"
              </div>
              {onCreateUser && (
                <button
                  type="button"
                  onClick={() => {
                    onCreateUser(query);
                    setQuery("");
                    setIsOpen(false);
                  }}
                  style={{
                    width: "100%",
                    padding: "8px",
                    background: "#f0f0f0",
                    border: "1px dashed #ccc",
                    borderRadius: "3px",
                    cursor: "pointer",
                    fontSize: "13px",
                    color: "#0066cc",
                    textDecoration: "underline",
                  }}
                >
                  + Create new user "{query}"
                </button>
              )}
            </div>
          )}
          {members.map((m) => (
            <div
              key={m.id}
              onClick={() => {
                onChange(String(m.id));
                setQuery("");
                setIsOpen(false);
              }}
              style={{
                padding: "10px 12px",
                cursor: "pointer",
                background: String(m.id) === String(value) ? "#e3f2fd" : "white",
                borderBottom: "1px solid #f0f0f0",
                fontSize: "13px",
              }}
              onMouseEnter={(e) => e.currentTarget.style.background = "#f5f5f5"}
              onMouseLeave={(e) => e.currentTarget.style.background = String(m.id) === String(value) ? "#e3f2fd" : "white"}
            >
              <div style={{ fontWeight: "500" }}>{m.name}</div>
              <div style={{ fontSize: "12px", color: "#666" }}>{m.email}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
