import { useEffect, useRef, useState } from "react";
import { useAuth } from "../../context/AuthContext";
 
const MAX_IMAGE_SIZE = 2 * 1024 * 1024; // 2MB
const ACCEPTED_TYPES = ["image/png", "image/jpeg", "image/gif"];
const DEFAULT_AVATAR =
  "data:image/svg+xml;utf8," +
  encodeURIComponent(
    `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 120 120'>
      <rect width='120' height='120' fill='#e5e5e5'/>
      <circle cx='60' cy='46' r='22' fill='#bdbdbd'/>
      <ellipse cx='60' cy='112' rx='40' ry='30' fill='#bdbdbd'/>
    </svg>`
  );
 
function UserCircleIcon() {
  return (
    <svg
      className="account__title-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="10" />
      <circle cx="12" cy="10" r="3.5" />
      <path d="M6.2 18.6a7 7 0 0 1 11.6 0" />
    </svg>
  );
}
 
function Field({ id, label, type = "text", value, onChange, placeholder, autoComplete }) {
  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        className="field__input"
        type={type}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        autoComplete={autoComplete}
      />
    </div>
  );
}
 

export default function AdminAccount({
  initialAvatar = DEFAULT_AVATAR,
  onUpdatePassword = (passwords) => console.log("Update password", passwords),
  onDeleteAccount = () => console.log("Delete account"),
}) {
  const { user, authFetch } = useAuth();
  const fileInputRef = useRef(null);
  const [avatar, setAvatar] = useState(initialAvatar);
  const [imageError, setImageError] = useState("");

  const [profile, setProfile] = useState({ email: "", firstName: "", lastName: "" });
  const [profileStatus, setProfileStatus] = useState({ type: "", message: "" });

  useEffect(() => {
    if (user) {
      setProfile({
        email: user.email || "",
        firstName: user.firstName || "",
        lastName: user.lastName || "",
      });
    }
  }, [user]);
  const [passwords, setPasswords] = useState({ current: "", next: "", confirm: "" });
  const [passwordError, setPasswordError] = useState("");
 
  const handleImageChange = (e) => {
    const file = e.target.files?.[0];
    e.target.value = ""; // allow picking the same file again
    if (!file) return;
 
    if (!ACCEPTED_TYPES.includes(file.type)) {
      setImageError("Choose a PNG, JPEG, or GIF image.");
      return;
    }
    if (file.size > MAX_IMAGE_SIZE) {
      setImageError("That image is over 2MB. Choose a smaller one.");
      return;
    }
 
    setImageError("");
    setAvatar(URL.createObjectURL(file));
  };
 
  const handleProfileChange = (key) => (e) =>
    setProfile((prev) => ({ ...prev, [key]: e.target.value }));
 
  const handlePasswordChange = (key) => (e) =>
    setPasswords((prev) => ({ ...prev, [key]: e.target.value }));
 
  const submitProfile = async (e) => {
    e.preventDefault();
    setProfileStatus({ type: "", message: "" });
    try {
      const res = await authFetch("/api/v1/users/me", {
        method: "PATCH",
        body: JSON.stringify({ firstName: profile.firstName, lastName: profile.lastName }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error || "Could not update profile");
      }
      setProfileStatus({ type: "success", message: "Profile updated." });
    } catch (err) {
      setProfileStatus({ type: "error", message: err.message || "Something went wrong." });
    }
  };
 
  const submitPassword = (e) => {
    e.preventDefault();
    if (!passwords.current || !passwords.next) {
      setPasswordError("Enter your current and new password.");
      return;
    }
    if (passwords.next !== passwords.confirm) {
      setPasswordError("New passwords don't match.");
      return;
    }
    setPasswordError("");
    onUpdatePassword({ current: passwords.current, next: passwords.next });
    setPasswords({ current: "", next: "", confirm: "" });
  };
 
  const handleDelete = () => {
    if (window.confirm("Permanently delete your account? This can't be undone.")) {
      onDeleteAccount();
    }
  };
 
  return (
    <main className="account">
      <h1 className="account__title">
        Account <UserCircleIcon /> Information
      </h1>
 
      {/* Avatar */}
      <section className="avatar">
        <img className="avatar__image" src={avatar} alt="Profile" />
        <div className="avatar__controls">
          <div className="avatar__buttons">
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => fileInputRef.current?.click()}
            >
              Change Image
            </button>
            <button
              type="button"
              className="btn btn--outline"
              onClick={() => {
                setAvatar(DEFAULT_AVATAR);
                setImageError("");
              }}
            >
              Remove Image
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept={ACCEPTED_TYPES.join(",")}
              onChange={handleImageChange}
              hidden
            />
          </div>
          <p className={`avatar__hint ${imageError ? "avatar__hint--error" : ""}`}>
            {imageError || "We support PNGs, JPEGs, and GIFs under 2MB"}
          </p>
        </div>
      </section>
 
      {/* Profile + security cards */}
      <div className="account__grid">
        <form className="card" onSubmit={submitProfile}>
          <h2 className="card__title">Profile Information</h2>
          <p className="card__subtitle">Account name information &amp; email for newsletter</p>
 
          <Field
            id="email"
            label="Email"
            type="email"
            value={profile.email}
            onChange={handleProfileChange("email")}
            readOnly
            placeholder="john@example.com"
            autoComplete="email"
          />
          <Field
            id="firstName"
            label="First Name"
            value={profile.firstName}
            onChange={handleProfileChange("firstName")}
            placeholder="John"
            autoComplete="given-name"
          />
          <Field
            id="lastName"
            label="Last Name"
            value={profile.lastName}
            onChange={handleProfileChange("lastName")}
            placeholder="Doe"
            autoComplete="family-name"
          />
 
          {profileStatus.message && (
            <p
              className={profileStatus.type === "error" ? "card__error" : "card__subtitle"}
              role={profileStatus.type === "error" ? "alert" : "status"}
            >
              {profileStatus.message}
            </p>
          )}

          <button type="submit" className="btn btn--outline btn--block">
            Update Information
          </button>
        </form>
 
        <form className="card" onSubmit={submitPassword}>
          <h2 className="card__title">Account Security</h2>
          <p className="card__subtitle">Account Password and security measures</p>
 
          <Field
            id="currentPassword"
            label="Current Password"
            type="password"
            value={passwords.current}
            onChange={handlePasswordChange("current")}
            placeholder="••••••••"
            autoComplete="current-password"
          />
          <Field
            id="newPassword"
            label="New Password"
            type="password"
            value={passwords.next}
            onChange={handlePasswordChange("next")}
            placeholder="••••••••"
            autoComplete="new-password"
          />
          <Field
            id="confirmPassword"
            label="Confirm New Password"
            type="password"
            value={passwords.confirm}
            onChange={handlePasswordChange("confirm")}
            placeholder="••••••••"
            autoComplete="new-password"
          />
 
          {passwordError && (
            <p className="card__error" role="alert">
              {passwordError}
            </p>
          )}
 
          <button type="submit" className="btn btn--outline btn--block">
            Update Password
          </button>
        </form>
      </div>
 
      {/* Support */}
      <section className="card card--support">
        <h2 className="card__title">Support</h2>
        <div className="support__row">
          <div>
            <h3 className="support__heading">Delete my Account</h3>
            <p className="card__subtitle">Permanently delete account and remove all access.</p>
          </div>
          <button type="button" className="btn btn--outline" onClick={handleDelete}>
            Delete Account
          </button>
        </div>
      </section>
    </main>
  );
}