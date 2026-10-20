import React, { useState, useEffect } from "react";
import { useAuth } from "../../context/AuthContext";
import "./HardwareCheckIns.css";

export default function HardwareCheckIns() {
  const { authFetch } = useAuth();
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
                    {checkIn.waiverSigned && !checkIn.waiverApprovedByAdmin && (
                      <>
                        <button
                          className="ApproveBtn"
                          onClick={() => approveWaiver(checkIn.id)}
                          title="Approve waiver"
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
    </div>
  );
}
