import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

/**
 * Card-based sign-in flow for NFC cards.
 * 
 * Two scenarios:
 * 1. REGISTERED CARD: Tap card → auto-login with JWT → redirect to role dashboard
 * 2. UNREGISTERED/DEACTIVATED CARD: Tap card → admin-assigned email → enter admin-sent OTP → login
 */
export default function CardSignInForm() {
  const navigate = useNavigate();
  const { setToken } = useAuth();
  
  const [step, setStep] = useState("waiting"); // waiting | card-found | sending-otp | entering-otp | processing
  const [cardUid, setCardUid] = useState("");
  const [email, setEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("Tap your NFC card to get started");
  const [loading, setLoading] = useState(false);
  const [cardStatus, setCardStatus] = useState(null); // unregistered

  // Listen for card taps via simulated card reader input
  useEffect(() => {
    const handleCardTap = async (e) => {
      const uid = e.currentTarget.value.trim();
      if (!uid) return;

      setLoading(true);
      setError("");
      setMessage("Checking card...");

      try {
        // First, register the card tap with the backend scan endpoint
        // This creates an UnregisteredCard if it doesn't exist
        await fetch("/api/v1/hardware/card-scan", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            cardUid: uid,
            deviceId: "sign-in-reader"
          })
        }).catch(() => {
          // Endpoint might not be available, continue anyway
        });

        // Now check if card exists in unregistered list
        const res = await fetch(`/api/v1/auth/card/check-by-uid?uid=${encodeURIComponent(uid)}`);
        const data = await res.json();

        if (!res.ok) {
          throw new Error("Failed to check card status");
        }

        setCardUid(uid);

        // Case 1: Card not found
        if (!data.found) {
          setError("Card not found in system. Please contact admin for access.");
          setLoading(false);
          e.currentTarget.value = "";
          return;
        }

        // Case 2: Card found in unregistered list
        setCardStatus("unregistered");
        setEmail(data.email || "");
        
        if (!data.email) {
          // Card was tapped but not yet assigned to an email
          setError("This card has not been assigned by an admin yet. Please contact admin.");
          setLoading(false);
          e.currentTarget.value = "";
          return;
        }

        if (!data.canSendOtp) {
          setError("This card is not yet ready. Please contact admin to complete the setup.");
          setLoading(false);
          e.currentTarget.value = "";
          return;
        }

        // Card is ready - show option to send OTP
        setStep("card-found");
        setMessage("Card recognized!");
      } catch (err) {
        setError(err.message || "Failed to check card. Please try again.");
      } finally {
        setLoading(false);
        e.currentTarget.value = "";
      }
    };

    const cardReaderInput = document.getElementById("card-reader-input");
    if (cardReaderInput) {
      cardReaderInput.addEventListener("change", handleCardTap);
      return () => cardReaderInput.removeEventListener("change", handleCardTap);
    }
  }, []);

  async function handleSendOtp(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    setStep("sending-otp");
    setMessage("Sending code to your email...");

    try {
      // Send OTP to user's email
      const res = await fetch("/api/v1/auth/card/send-otp", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, cardUid }),
      });

      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(
          data?.message ||
          (res.status >= 500
            ? "Server error sending code. Please try again."
            : "Failed to send code. Please try again.")
        );
      }

      // Move to OTP entry step
      setStep("entering-otp");
      setMessage("Enter the code we just sent to your email");
    } catch (err) {
      setError(err.message);
      setStep("card-found");
    } finally {
      setLoading(false);
    }
  }

  async function handleVerifyOtp(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    setStep("processing");
    setMessage("Verifying code and signing you in...");

    try {
      // Verify OTP and get JWT token
      const verifyRes = await fetch("/api/v1/auth/card/verify-otp", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, otp }),
      });

      const verifyData = await verifyRes.json().catch(() => null);
      if (!verifyRes.ok) {
        throw new Error(
          verifyData?.message ||
          (verifyRes.status >= 500
            ? "Server error verifying code. Please try again."
            : "Invalid or expired code. Please try again.")
        );
      }

      // Store the JWT token for authentication
      const jwtToken = verifyData.access_token;
      setToken(jwtToken);

      // Now activate the card with the JWT token in Authorization header
      const activateRes = await fetch("/api/v1/auth/card/activate", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${jwtToken}`
        },
        body: JSON.stringify({ cardUid }),
      });

      const activateData = await activateRes.json().catch(() => ({}));
      if (!activateRes.ok) {
        console.warn("Card activation warning:", activateData?.message);
      }

      // Success - redirect to dashboard
      setMessage("Success! Redirecting to your dashboard...");
      setTimeout(() => {
        navigate("/dashboard", { replace: true });
      }, 1000);
    } catch (err) {
      setError(err.message);
      setStep("entering-otp");
    } finally {
      setLoading(false);
    }
  }

  function handleReset() {
    setStep("waiting");
    setCardUid("");
    setEmail("");
    setOtp("");
    setError("");
    setMessage("Tap your NFC card to get started");
    setCardStatus(null);
  }

  // Waiting for card tap
  if (step === "waiting" || !cardStatus) {
    return (
      <div className="card-signin-form">
        <div className="card-message">
          <p>{message}</p>
        </div>

        {/* Hidden input for card reader simulation */}
        <input
          id="card-reader-input"
          type="hidden"
          autoFocus
          aria-hidden="true"
        />

        {/* For testing/demo purposes - show manual card UID input */}
        <div className="card-demo-section">
          <p className="demo-label">Demo: Manually enter card UID</p>
          <input
            type="text"
            placeholder="Enter card UID (e.g., 85F181D4)"
            onKeyPress={(e) => {
              if (e.key === "Enter") {
                const event = new Event("change", { bubbles: true });
                const input = document.getElementById("card-reader-input");
                if (input) {
                  input.value = e.currentTarget.value;
                  input.dispatchEvent(event);
                }
              }
            }}
            disabled={loading}
          />
          <p className="demo-note">Press Enter to simulate card tap</p>
        </div>

        {error && (
          <p className="error-message" role="alert">
            {error}
          </p>
        )}

        {loading && <p className="loading-message">Processing...</p>}

        <div className="card-info-box">
          <h4>How it works:</h4>
          <ul>
            <li>Tap your card</li>
            <li>Click "Send Code" to request a code</li>
            <li>Check your email for the code</li>
            <li>Enter the code to sign in</li>
          </ul>
        </div>
      </div>
    );
  }

  // Card found - prompt to send OTP
  if (step === "card-found" || step === "sending-otp") {
    return (
      <form onSubmit={handleSendOtp} className="card-signin-form">
        <div className="card-info">
          <p className="card-email-label">Card registered to:</p>
          <p className="card-email-value">{email || "(No email assigned)"}</p>
        </div>

        <div className="message">
          <p>{message}</p>
        </div>

        {error && (
          <p className="error-message" role="alert">
            {error}
          </p>
        )}

        <div className="sign-in">
          <button type="submit" disabled={loading} className="signButton">
            {loading ? "Sending code..." : "Send Code to Email"}
          </button>
        </div>

        <div className="reset-link">
          <button type="button" onClick={handleReset} disabled={loading} className="textButton">
            Use different card
          </button>
        </div>
      </form>
    );
  }

  // Entering OTP after code sent
  if (step === "entering-otp" || step === "processing") {
    return (
      <form onSubmit={handleVerifyOtp} className="card-signin-form">
        <div className="card-info">
          <p className="card-email-label">Verification code sent to:</p>
          <p className="card-email-value">{email}</p>
        </div>

        <div className="message">
          <p>{message}</p>
        </div>

        <div className="inputColumn">
          <label>One-Time Code</label>
          <input
            type="text"
            inputMode="numeric"
            pattern="[0-9]{6}"
            maxLength={6}
            placeholder="6-digit code"
            value={otp}
            onChange={(e) => setOtp(e.target.value)}
            required
            disabled={loading}
            autoFocus
          />
          <p className="field-hint">Check your email for the code</p>
        </div>

        {error && (
          <p className="error-message" role="alert">
            {error}
          </p>
        )}

        <div className="sign-in">
          <button type="submit" disabled={loading} className="signButton">
            {loading ? "Signing in..." : "Sign In"}
          </button>
        </div>

        <div className="reset-link">
          <button type="button" onClick={handleReset} disabled={loading} className="textButton">
            Use different card
          </button>
        </div>
      </form>
    );
  }

  return null;
}

