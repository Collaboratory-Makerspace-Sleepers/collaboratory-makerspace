import AllClasses from "./AllClasses";
import ReservationsTable from "./ReservationsTable";
export default function AdminBookClasses() {
 return (
     <div className="AdUser">
       <p>Classes</p>
 
       <div className="AdUser1">
         <div className="AdUser2">
           <div className="AdUser3">
             <p>Create a new class</p>
             <p>Create a new class for the makerspace</p>
           </div>
           <button>Create New Class</button>
         </div>
       </div>
 
       <div className="AdAct">
         <AllClasses />
       </div>

       <div className="AdAct">
         <ReservationsTable isClass title="Class Reservations" />
       </div>
     </div>
 )
}