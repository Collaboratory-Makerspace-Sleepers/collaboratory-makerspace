import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import mainImage from "./assets/image.png";
import "./App.css";
import Makerspacecarousel from "./Makerspacecarousel";

export default function Home() {
  const navigate = useNavigate();

  return (
    <div className="bigdiv">
      <div className="secondDiv">
        <div className="NavBar">
          <p>The Collaboratory</p>

          <div className="links">
            <Link to="/">Home</Link>
            <a>Pricing</a>
            <button onClick={() => navigate("/signup")}>Sign Up</button>
            <button onClick={() => navigate("/signin")}>Sign in</button>
          </div>
        </div>

        <div className="Colla">
          <p>The Collaboratory</p>

          <p>
            The collaboratory is a makerspace that offers state-of-the-art
            equipment, class bookings, and private studios
          </p>

          <div className="CollaRow">
            <button onClick={() => navigate("/signup")}>Try for free today</button>
            <button onClick={() => navigate("/signin")}>Sign in</button>
          </div>
        </div>

        <div className="OrangeDiv">
          <img src={mainImage} alt="Collaboratory" />
        </div>

        <div className="WhiteDiv">
          <div className="WhiteDiv1">
            <p>Rent Equipment, Book Classes</p>

            <div className="WhiteDivRow">
              <p className="WhiteDivText">
                The collaboratory is a makerspace where you can rent top-notch
                euqipment at budget-friendly prices, along with options for
                class bookings and private studios.
              </p>
              <button>Start your project today</button>
            </div>
          </div>

          <Makerspacecarousel />
        </div>

        <div className="PrivateDiv">
          <div className="SquareRow">
            <div className="Square1"></div>
            <div className="Square2"></div>
          </div>
          <div className="MiddleDiv">
            <p>Private Studios</p>
            <p className="MiddeText">
              The collaboratory is a vibrant makerspace where you can easily
              book classes to learn and create using top-notic equipment and
              private studios.
            </p>
            <button>View Membership Options</button>
          </div>
          <div className="SquareRow">
            <div className="Square3"></div>
            <div className="Square4"></div>
          </div>
        </div>

        <div className="FootDiv">
          <div className="FootDiv12">
            <div className="FootDiv2">
              <p>The Collaboratory</p>
              <p>
                The collaboratory is a makerspace that offers state-of-the-art
                equipment, class bookings, and private studios.
              </p>
            </div>

            <div className="FootDivGrid">
              <Link to="/">Home</Link>
              <Link to="/signin">Sign in</Link>
              <a>Youtube</a>
              <a>About</a>
              <Link to="/signup">Sign Up</Link>
              <a>Facebook</a>
              <a>Pricing</a>
              <a>Contact</a>
              <a>Instagram</a>
            </div>
          </div>
          <div className="FootDiv3">
            <p>All Rights Reserved, 2026</p>
            <a>Back to Top</a>
          </div>
        </div>
      </div>
    </div>
  );
}
