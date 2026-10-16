import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

/**
 * Blocks a subtree until the caller's permission set is known, then admits it
 * only if they hold at least one of `anyOf`.
 *
 * This is a presentation guard only. Every endpoint the admin panel calls is
 * independently enforced server-side by the filter chains in SecurityConfig and
 * the @PreAuthorize expressions on the controllers, so hiding a route here is
 * never the thing that actually protects the data.
 */
export default function RequirePermission({ anyOf, children }) {
  const { isAuthenticated, permissions, loadingProfile } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  // Permissions are fetched asynchronously after sign-in. Rendering a redirect
  // before they land would bounce an admin out of the panel on every reload.
  if (loadingProfile) {
    return <p className="AdminLoading">Checking permissions…</p>;
  }

  if (!anyOf.some((code) => permissions.includes(code))) {
    return (
      <div className="AdminDenied">
        <h2>Access denied</h2>
        <p>Your account does not have permission to view this page.</p>
        <p className="AdminDeniedHint">
          Required: {anyOf.join(" or ")}
        </p>
      </div>
    );
  }

  return children;
}
