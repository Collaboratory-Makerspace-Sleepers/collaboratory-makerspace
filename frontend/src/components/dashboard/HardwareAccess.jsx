import { useState } from "react";
import { useNavigate, useOutletContext } from "react-router-dom";
import RabbitSignaturePad from "../admin-dashboard/RabbitSignaturePad";
import { WAIVER_SECTIONS } from "./waiverSections";
import "./HardwareAccess.css";

export default function HardwareAccess() {
  const { hardware } = useOutletContext();
  const { current, loaded, refresh, dismissApproved } = hardware;
  const navigate = useNavigate();
  const [agreed, setAgreed] = useState(false);

  if (!loaded) return <p className="AdminLoading">Loading…</p>;

  if (!current) {
    return (
      <div className="HWAccess">
        <h2>Hardware Access</h2>
        <p className="HWAccessMuted">
          No access request right now. Tap your card at the equipment and this page
          will guide you through the next step.
        </p>
      </div>
    );
  }

  const equipment = current.equipmentName;

  if (current.status === "NEEDS_WAIVER") {
    return (
      <div className="HWAccess">
        <h2>Waiver required for {equipment}</h2>
        <p className="HWAccessMuted">
          Read the waiver, then sign below. Staff will review it before you can use the equipment.
        </p>

        <div className="HWWaiverText">
          {WAIVER_SECTIONS.map((section) => (
            <div key={section.title}>
              <h4>{section.title}</h4>
              <p>{section.text}</p>
            </div>
          ))}
        </div>

        <label className="HWAgree">
          <input
            type="checkbox"
            checked={agreed}
            onChange={(e) => setAgreed(e.target.checked)}
          />
          I have read and agree to the waiver above.
        </label>

        {agreed && (
          <RabbitSignaturePad
            checkInId={current.id}
            onCancel={() => setAgreed(false)}
            onSignatureSaved={refresh}
          />
        )}
      </div>
    );
  }

  if (current.status === "WAIVER_SIGNED") {
    return (
      <div className="HWAccess HWCenter" role="status" aria-live="polite">
        <div className="HWSpinner" aria-hidden="true" />
        <h2>Waiting for approval</h2>
        <p>
          Your waiver for <strong>{equipment}</strong> was received. A staff member or
          admin needs to approve it before you can continue.
        </p>
        <p className="HWAccessMuted">
          Keep this page open. It updates on its own as soon as you are approved.
        </p>
      </div>
    );
  }

  if (current.status === "WAIVER_APPROVED") {
    return (
      <div className="HWAccess HWCenter" role="status">
        <div className="HWCheck" aria-hidden="true">✓</div>
        <h2>You're approved</h2>
        <p>
          Your waiver for <strong>{equipment}</strong> was approved. Tap your card at the
          equipment to start.
        </p>
        <button
          className="HWContinue"
          onClick={() => { dismissApproved(); navigate("/dashboard/rentequipment"); }}
        >
          Continue
        </button>
      </div>
    );
  }

  return (
    <div className="HWAccess HWCenter" role="alert">
      <div className="HWDenied" aria-hidden="true">✗</div>
      <h2>Access denied</h2>
      <p>
        Access to <strong>{equipment}</strong> was denied.
        {current.errorMessage ? ` Reason: ${current.errorMessage}` : ""}
      </p>
      <p className="HWAccessMuted">Please speak with a staff member.</p>
    </div>
  );
}
