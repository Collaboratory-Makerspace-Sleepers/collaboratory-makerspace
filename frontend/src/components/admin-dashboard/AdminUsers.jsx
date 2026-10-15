import { useState } from "react";
import AllUsers from "./AllUsers";
import UserInformation from "./UserInformation";

export default function AdminUsers() {
  const [selectedUser, setSelectedUser] = useState(null);

  return (
    <div className="AdUserRow">
      <div className="AdUser">
        <p>Users</p>

        <div className="AdUser1">
          <div className="AdUser2">
            <div className="AdUser3">
              <p>Create Staff/Instructor</p>
              <p>Create a new staff employee or instructor for classes</p>
            </div>
            <button>Create New Staff</button>
          </div>
          <div className="AdUser2">
            <div className="AdUser3">
              <p>Create a new user</p>
              <p>Create a new user to access the makerspace</p>
            </div>
            <button>Create New User</button>
          </div>
        </div>

        <div className="AdAct">
          <AllUsers
            onViewProfile={setSelectedUser}
            selectedId={selectedUser?.id}
          />
        </div>
      </div>
      {selectedUser && (
        <UserInformation
          key={selectedUser.id}
          user={selectedUser}
          onClose={() => setSelectedUser(null)}
        />
      )}
    </div>
  );
}
