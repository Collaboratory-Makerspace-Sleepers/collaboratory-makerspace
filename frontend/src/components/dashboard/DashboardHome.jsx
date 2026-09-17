import { useState } from "react";
import Makerspacecarousel from "../Makerspacecarousel";

export default function DashboardHome() {
  return (
    <div className="DashHome1">
      <div className="Dash2">
        <p>Hi Kolbe</p>
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

      <div className="Dash6">
        <div className="Dash7">
          <p>Recommended</p>
          <div className="Dash8">
            <button>Equipment</button>
            <button>Classes</button>
          </div>
        </div>
        <div className="RentGrid">
        <div className="Rent1">
          <div>
            <img src="https://picsum.photos/seed/laser-a/480/360" />
          </div>

          <div className="Rent2">
            <div className="Rent3">
              <div className="Rent4">
                <p>2/6 Available</p>
              </div>
              <p>LAserMachine</p>
            </div>

            <div className="Rent5">
              <p>$7/hr</p>
              <button>Reserve</button>
            </div>
          </div>
        </div>
        <div className="Rent1">
          <div>
            <img src="https://picsum.photos/seed/laser-a/480/360" />
          </div>

          <div className="Rent2">
            <div className="Rent3">
              <div className="Rent4">
                <p>2/6 Available</p>
              </div>
              <p>LAserMachine</p>
            </div>

            <div className="Rent5">
              <p>$7/hr</p>
              <button>Reserve</button>
            </div>
          </div>
        </div>
        <div className="Rent1">
          <div>
            <img src="https://picsum.photos/seed/laser-a/480/360" />
          </div>

          <div className="Rent2">
            <div className="Rent3">
              <div className="Rent4">
                <p>2/6 Available</p>
              </div>
              <p>LAserMachine</p>
            </div>

            <div className="Rent5">
              <p>$7/hr</p>
              <button>Reserve</button>
            </div>
          </div>
        </div>
       
      </div>
      </div>

      <div className="Dash9">
        <div>
          <p>Recommended Tasks</p>
        </div>
        <div className="Dash10">
          <p>Intro the laser cutting</p>
          <button>Complete</button>
        </div>
         <div className="Dash10">
          <p>Intro the laser cutting</p>
          <button>Complete</button>
        </div>
         <div className="Dash10">
          <p>Intro the laser cutting</p>
          <button>Complete</button>
        </div>
      </div>
    </div>
  );
}
