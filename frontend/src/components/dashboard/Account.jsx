export default function Account() {
  return (
    <div className="AccountDiv">
      <div className="AccountD1">
        <p>Account Information</p>
        <p>
          Welcome back. Today is May 12th 2026. You have one upcoming
          reservation at 10:15 today.
        </p>
      </div>

      <div className="AccountD2">
        <div className="AccountD3">
          <p>Profile Information</p>

          <div className="AccountD4">
            <p>K</p>
            <p>Kolbe Yang</p>
            <p>Makerspace Member</p>
          </div>

          <div className="AccountD5">
            <button>Change Image</button>
            <p>We support PNG, JPEGs, and GIFs under 2MB</p>
          </div>

          {/* form input */}
          <div className="AccountFormRow">
            <div className="AccountFormField">
              <label>First Name</label>
              <input type="text" placeholder="First Name" />
            </div>
            <div className="AccountFormField">
              <label>Last Name</label>
              <input type="text" placeholder="Last Name" />
            </div>
          </div>

          <div className="AccountFormField">
            <label>Email</label>
            <input type="text" placeholder="John" />
          </div>

          <button>Update Information</button>
        </div>
        <div className="AccountD3">
          <p>Account Security</p>

          <div className="AccountFormField">
            <label>Current Password</label>
            <input type="text" placeholder="John" />
          </div>

          <div className="AccountFormField">
            <label>New Password</label>
            <input type="text" placeholder="John" />
          </div>

          <div className="AccountFormField">
            <label>Confirm New Password</label>
            <input type="text" placeholder="John" />
          </div>

          <button>Update Password</button>
        </div>
      </div>
    </div>
  );
}
