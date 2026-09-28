import {
  useDashboard,
  todaySentence,
  reservationSentence,
} from "../../hooks/useDashboard";

const PLANS = [
  {
    code: "GUEST",
    name: "Guest",
    price: "$0/Month",
    features: [
      "Ability to rent equipment",
      "Ability to book classes",
      "Access to events and discounts",
    ],
  },
  {
    code: "MEMBER",
    name: "Member",
    price: "$95/Month",
    features: [
      "Rent Equipment for free",
      "Access to common space",
      "Book classes for free",
      "Access to events and discounts",
    ],
  },
  {
    code: "STUDIO",
    name: "Member with Private Studio Space",
    price: "$350-750/Month",
    features: [
      "Rent Equipment for free",
      "Access to common space",
      "Book classes for free",
      "Access to events and discounts",
      "Private studio space",
    ],
  },
];

export default function Membership() {
  const { reservations, loading, error } = useDashboard();

  return (
    <div className="DashHome1">
      <div className="Dash2">
        <p>Membership</p>
        <p>
          Welcome back. {todaySentence()}{" "}
          {!loading && !error && reservationSentence(reservations)}
        </p>
      </div>

      <div className="Dash3">
        <p>Current Plan</p>
        <div className="Dash4">
          <p>Guest</p>
          <div className="Dash5">
            <button>Upgrade</button>
          </div>
        </div>
      </div>

      <div className="CollaR1">
        {PLANS.map((plan, index) => (
          <div className="colla6" key={plan.code}>
            <div className="collar2">
              <div className="collar3">
                <p>{index === 0 ? "Current Plan" : "Membership"}</p>
              </div>

              <div className="colla4">
                <p>{plan.name}</p>
                <p>{plan.price}</p>
              </div>
            </div>
            <div className="colla5">
              {plan.features.map((feature) => (
                <p key={feature}>{feature}</p>
              ))}
            </div>

            <button>{index === 0 ? "Current Plan" : "Upgrade"}</button>
          </div>
        ))}
      </div>
    </div>
  );
}