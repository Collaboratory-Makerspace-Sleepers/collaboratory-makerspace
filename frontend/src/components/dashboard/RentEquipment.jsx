import { useNavigate } from "react-router-dom";
import equipment from "../../data/equipment";

export default function RentEquipment() {
  const navigate = useNavigate();

  return (
    <div className="DashHome1">
      <div className="Dash2">
        <p>Rent Equipment</p>
        <p>
          Welcome back. Today is May 12th 2026. You have one upcoming
          reservation at 10:15 today.
        </p>
      </div>

      <div className="Dash3">
        <p>Upcoming Reservations</p>
        <div className="Dash4">
          <p>Intro the laser Cutting</p>
          <div className="Dash5">
            <button>View</button>
            <button>Edit</button>
          </div>
        </div>
      </div>

      <div className="RentEquipmentSection">
        {/* Search field */}
        <input type="text" placeholder="Search" className="SearchInput" />

        <div className="RentGrid">
          {equipment.map((item) => (
            <div className="Rent1" key={item.id}>
              <div>
                <img src={item.image} />
              </div>

              <div className="Rent2">
                <div className="Rent3">
                  <div className="Rent4">
                    <p>
                      {item.available}/{item.total} Available
                    </p>
                  </div>
                  <p>{item.name}</p>
                </div>

                <div className="Rent5">
                  <p>{item.price}</p>
                  <button onClick={() => navigate(`/dashboard/rentequipment/${item.id}`)}>
                    Reserve
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
