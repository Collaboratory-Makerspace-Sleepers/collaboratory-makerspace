import React, { useRef, useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import "./RabbitSignaturePad.css";

/**
 * RabbitSignaturePad Component
 * Allows users to draw signatures on a canvas with touch and mouse support.
 * Implements rabbit-style smooth signature drawing.
 */
export default function RabbitSignaturePad({ checkInId, submitUrl, extraBody, onSignatureSaved, onCancel }) {
  const canvasRef = useRef(null);
  const { authFetch } = useAuth();
  const [isDrawing, setIsDrawing] = useState(false);
  const [context, setContext] = useState(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    // Set canvas size
    canvas.width = canvas.offsetWidth;
    canvas.height = canvas.offsetHeight;

    const ctx = canvas.getContext("2d");
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.lineWidth = 2;
    ctx.strokeStyle = "#333";

    setContext(ctx);
  }, []);

  const startDrawing = (e) => {
    setIsDrawing(true);
    const { offsetX, offsetY } = getCoordinates(e);
    context?.beginPath();
    context?.moveTo(offsetX, offsetY);
  };

  const draw = (e) => {
    if (!isDrawing) return;

    const { offsetX, offsetY } = getCoordinates(e);
    context?.lineTo(offsetX, offsetY);
    context?.stroke();
  };

  const stopDrawing = () => {
    setIsDrawing(false);
    context?.closePath();
  };

  const getCoordinates = (e) => {
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    
    // Handle both mouse and touch events
    const clientX = e.clientX || (e.touches && e.touches[0].clientX);
    const clientY = e.clientY || (e.touches && e.touches[0].clientY);

    return {
      offsetX: clientX - rect.left,
      offsetY: clientY - rect.top,
    };
  };

  const clearSignature = () => {
    const canvas = canvasRef.current;
    context?.clearRect(0, 0, canvas.width, canvas.height);
  };

  const saveSignature = async () => {
    const canvas = canvasRef.current;
    const signatureData = canvas.toDataURL("image/png");

    try {
      const response = await authFetch(
        submitUrl || `/api/v1/hardware/checkins/${checkInId}/sign-waiver`,
        {
          method: "POST",
          body: JSON.stringify({ ...extraBody, signatureData }),
        }
      );

      if (!response.ok) throw new Error("Failed to save signature");

      const data = await response.json();
      onSignatureSaved(data);
    } catch (error) {
      console.error("Error saving signature:", error);
      alert("Failed to save signature: " + error.message);
    }
  };

  return (
    <div className="RabbitSignaturePadContainer">
      <div className="SignatureHeader">
        <h3>Sign Waiver</h3>
        <p className="Instructions">Please sign below to acknowledge and accept the waiver</p>
      </div>

      <canvas
        ref={canvasRef}
        className="SignatureCanvas"
        onMouseDown={startDrawing}
        onMouseMove={draw}
        onMouseUp={stopDrawing}
        onMouseLeave={stopDrawing}
        onTouchStart={startDrawing}
        onTouchMove={draw}
        onTouchEnd={stopDrawing}
      />

      <div className="SignatureActions">
        <button className="ClearBtn" onClick={clearSignature}>
          Clear Signature
        </button>
        <div className="ConfirmActions">
          <button className="CancelBtn" onClick={onCancel}>
            Cancel
          </button>
          <button className="SubmitBtn" onClick={saveSignature}>
            Submit Signature
          </button>
        </div>
      </div>
    </div>
  );
}
