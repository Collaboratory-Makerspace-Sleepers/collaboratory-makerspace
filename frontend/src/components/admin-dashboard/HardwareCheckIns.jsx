import React, { useState, useEffect } from "react";
import { useAuth } from "../../context/AuthContext";
import CardScansLive from "./CardScansLive";
import CardManager from "./CardManager";
import UnregisteredCardsManager from "./UnregisteredCardsManager";
import "./HardwareCheckIns.css";

function CheckInLog() {
  const { authFetch } = useAuth();
  const [signingId, setSigningId] = useState(null);
  const [viewSignature, setViewSignature] = useState(null);
  const [signName, setSignName] = useState("");
  const [signBusy, setSignBusy] = useState(false);
  const [checkIns, setCheckIns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [filter, setFilter] = useState("all");
  const [page, setPage] = useState(0);

  useEffect(() => {
    fetchCheckIns();
  }, [filter, page]);

  const fetchCheckIns = async () => {
    setLoading(true);
    try {
      const endpoint = filter === "waivers" 
        ? `/api/v1/hardware/checkins/pending-waivers?page=${page}&size=20`
        : `/api/v1/hardware/checkins/recent?page=${page}&size=20`;

      const response = await authFetch(endpoint);

      if (!response.ok) throw new Error(`Failed to fetch check-ins (HTTP ${response.status})`);
      setError(null);
      
      const data = await response.json();
      setCheckIns(data.content || []);
    } catch (err) {
      setError(err.message);
      console.error("Error fetching check-ins:", err);
    } finally {
      setLoading(false);
    }
  };

  const approveWaiver = async (checkInId) => {
    try {
      const response = await authFetch(`/api/v1/hardware/checkins/${checkInId}/approve-waiver`, {
        method: "POST",
      });

      if (!response.ok) throw new Error("Failed to approve waiver");
      
      // Refresh the list
      fetchCheckIns();
    } catch (err) {
      console.error("Error approving waiver:", err);
      alert("Failed to approve waiver: " + err.message);
    }
  };

  const denyAccess = async (checkInId) => {
    const reason = prompt("Enter reason for denial:");
    if (!reason) return;

    try {
      const response = await authFetch(`/api/v1/hardware/checkins/${checkInId}/deny`, {
        method: "POST",
        body: JSON.stringify({ reason }),
      });

      if (!response.ok) throw new Error("Failed to deny access");
      
      fetchCheckIns();
    } catch (err) {
      console.error("Error denying access:", err);
      alert("Failed to deny access: " + err.message);
    }
  };

  const showSignature = async (checkInId) => {
    try {
      const response = await authFetch(`/api/v1/hardware/checkins/${checkInId}/signature`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      setViewSignature({ ...data, checkInId });
    } catch (err) {
      alert("Failed to load signature: " + err.message);
    }
  };

  const openSignedPdf = async () => {
    try {
      const response = await authFetch(`/api/v1/hardware/checkins/${viewSignature.checkInId}/signed-document`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const blob = await response.blob();
      window.open(URL.createObjectURL(blob), "_blank", "noopener");
    } catch (err) {
      alert("Failed to open the signed PDF: " + err.message);
    }
  };

  const uploadSignedPdf = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    const body = new FormData();
    body.append("file", file);
    try {
      const response = await authFetch(
        `/api/v1/hardware/checkins/${viewSignature.checkInId}/signed-document`,
        { method: "POST", body }
      );
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.message || `HTTP ${response.status}`);
      }
      setViewSignature((prev) => ({ ...prev, hasDocument: true }));
    } catch (err) {
      alert("Failed to upload the PDF: " + err.message);
    }
  };

  const submitSign = async (event) => {
    event.preventDefault();
    if (signName.trim().length < 2 || signBusy) return;
    setSignBusy(true);
    try {
      const response = await authFetch(`/api/v1/hardware/checkins/${signingId}/sign-waiver`, {
        method: "POST",
        body: JSON.stringify({ signedName: signName.trim() }),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      setSigningId(null);
      setSignName("");
      fetchCheckIns();
    } catch (err) {
      alert("Failed to sign waiver: " + err.message);
    } finally {
      setSignBusy(false);
    }
  };

  const getStatusBadge = (status) => {
    const statusMap = {
      SUCCESS: "success",
      FAILED: "failed",
      PENDING: "pending",
      NEEDS_WAIVER: "warning",
      WAIVER_SIGNED: "info",
      WAIVER_APPROVED: "success",
      DENIED: "danger",
    };
    return statusMap[status] || "default";
  };

  if (loading) return <div className="HardwareLoading">Loading check-ins...</div>;
  if (error) return <div className="HardwareError">Error: {error}</div>;

  return (
    <div className="HardwareCheckInsContainer">
      <div className="HWHeader">
        <h2>Hardware Access Logs</h2>
        <div className="HWFilters">
          <button
            className={`FilterBtn ${filter === "all" ? "active" : ""}`}
            onClick={() => { setFilter("all"); setPage(0); }}
          >
            All Check-ins
          </button>
          <button
            className={`FilterBtn ${filter === "waivers" ? "active" : ""}`}
            onClick={() => { setFilter("waivers"); setPage(0); }}
          >
            Pending Waivers ({checkIns.filter(c => c.waiverSigned && !c.waiverApprovedByAdmin).length})
          </button>
        </div>
      </div>

      <div className="HWTable">
        {checkIns.length === 0 ? (
          <p className="NoData">No check-ins found</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>User</th>
                <th>Equipment</th>
                <th>Status</th>
                <th>Waiver</th>
                <th>Access</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {checkIns.map((checkIn) => (
                <tr key={checkIn.id} className={`status-${getStatusBadge(checkIn.status)}`}>
                  <td>
                    <small>{new Date(checkIn.checkinTime).toLocaleString()}</small>
                  </td>
                  <td>
                    <strong>{checkIn.userName}</strong>
                    <br />
                    <small>{checkIn.userEmail}</small>
                  </td>
                  <td>
                    <strong>{checkIn.equipmentName}</strong>
                    <br />
                    <small>Device: {checkIn.deviceId}</small>
                  </td>
                  <td>
                    <span className={`Badge badge-${getStatusBadge(checkIn.status)}`}>
                      {checkIn.status}
                    </span>
                  </td>
                  <td>
                    {checkIn.waiverSigned ? (
                      <span className="WaiverStatus signed">✓ Signed</span>
                    ) : (
                      <span className="WaiverStatus pending">- Unsigned</span>
                    )}
                  </td>
                  <td>
                    {checkIn.accessGranted ? (
                      <span className="AccessGranted">✓ Granted</span>
                    ) : (
                      <span className="AccessDenied">✗ Denied</span>
                    )}
                    {checkIn.errorMessage && (
                      <br />
                    )}
                    {checkIn.errorMessage && (
                      <small className="ErrorMsg">{checkIn.errorMessage}</small>
                    )}
                  </td>
                  <td className="Actions">
                    {!checkIn.waiverSigned && checkIn.status === "NEEDS_WAIVER" && (
                      <>
                        <button
                          className="SignBtn"
                          onClick={() => setSigningId(checkIn.id)}
                          title="Collect the member's signature on the waiver"
                        >
                          Sign Waiver
                        </button>
                        <button
                          className="DenyBtn"
                          onClick={() => denyAccess(checkIn.id)}
                          title="Deny access"
                        >
                          Deny
                        </button>
                      </>
                    )}
                    {checkIn.waiverSigned && !checkIn.waiverApprovedByAdmin && (
                      <>
                        {checkIn.hasSignature && (
                          <button
                            className="ViewBtn"
                            onClick={() => showSignature(checkIn.id)}
                            title="View signature"
                          >
                            View Signature
                          </button>
                        )}
                        <button
                          className="ApproveBtn"
                          onClick={() => approveWaiver(checkIn.id)}
                          title="Approve waiver and allow access"
                        >
                          Approve
                        </button>
                        <button
                          className="DenyBtn"
                          onClick={() => denyAccess(checkIn.id)}
                          title="Deny access"
                        >
                          Deny
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {checkIns.length > 0 && (
        <div className="HWPagination">
          <button
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >
            Previous
          </button>
          <span>Page {page + 1}</span>
          <button onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}

      {signingId !== null && (
        <div className="HWModalOverlay">
          <div className="HWModal">
            <form onSubmit={submitSign}>
              <h3>Sign waiver</h3>
              <p>Type the member's full name to sign with RabbitSign.</p>
              <input
                className="HWNameInput"
                autoFocus
                value={signName}
                onChange={(e) => setSignName(e.target.value)}
                placeholder="Full name"
              />
              <div className="HWModalActions">
                <button type="button" className="ViewBtn" onClick={() => setSigningId(null)}>Cancel</button>
                <button type="submit" className="ViewBtn" disabled={signName.trim().length < 2 || signBusy}>
                  {signBusy ? "Signing…" : "Sign"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {viewSignature && (
        <div className="HWModalOverlay" onClick={() => setViewSignature(null)}>
          <div className="HWModal" onClick={(e) => e.stopPropagation()}>
            <h3>Signature</h3>
            <div className="HWSignedDoc">
              {viewSignature.hasDocument && (
                <button className="ViewBtn" onClick={openSignedPdf}>Open signed RabbitSign PDF</button>
              )}
              <label className="ViewBtn HWUploadBtn">
                Upload signed PDF
                <input type="file" accept="application/pdf" hidden onChange={uploadSignedPdf} />
              </label>
            </div>
            <button className="ViewBtn" onClick={() => setViewSignature(null)}>Close</button>
          </div>
        </div>
      )}
    </div>
  );
}

const TABS = [
  { id: "new-cards", label: "New Cards" },
  { id: "taps", label: "Card taps" },
  { id: "cards", label: "Cards" },
  { id: "log", label: "Waivers & check-ins" },
];

export default function HardwareCheckIns() {
  const [tab, setTab] = useState("new-cards");
  return (
    <div className="HardwareTabs">
      <div className="HWTabBar" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.id}
            role="tab"
            aria-selected={tab === t.id}
            className={`HWTab ${tab === t.id ? "active" : ""}`}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === "new-cards" && <UnregisteredCardsManager />}
      {tab === "taps" && <CardScansLive onAssigned={() => setTab("cards")} />}
      {tab === "cards" && <CardManager />}
      {tab === "log" && <CheckInLog />}
    </div>
  );
}
