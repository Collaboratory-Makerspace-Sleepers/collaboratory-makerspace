import React, { useRef, useLayoutEffect } from "react";

const MACHINES = [
  { id: "mach-0", name: "Laser cutter", availability: "0/7", trainingRequired: false, price: 7, image: "https://picsum.photos/seed/laser-a/480/360" },
  { id: "mach-1", name: "Laser cutter", availability: "2/7", trainingRequired: true,  price: 7, image: "https://picsum.photos/seed/laser-b/480/360" },
  { id: "mach-2", name: "Laser cutter", availability: "2/7", trainingRequired: false, price: 7, image: "https://picsum.photos/seed/laser-c/480/360" },
  { id: "mach-3", name: "Laser cutter", availability: "2/7", trainingRequired: true,  price: 7, image: "https://picsum.photos/seed/laser-d/480/360" },
  { id: "mach-4", name: "Laser cutter", availability: "2/7", trainingRequired: false, price: 7, image: "https://picsum.photos/seed/laser-e/480/360" },
  { id: "mach-5", name: "Laser cutter", availability: "1/7", trainingRequired: false, price: 7, image: "https://picsum.photos/seed/laser-f/480/360" },
  { id: "mach-6", name: "Laser cutter", availability: "3/7", trainingRequired: true,  price: 7, image: "https://picsum.photos/seed/laser-g/480/360" },
];

const CLASSES = [
  { id: "class-0", name: "Intro to laser cutting", status: "Available", when: "In 3 days", duration: "One hour", trainingRequired: true,  price: 25, image: "https://picsum.photos/seed/class-a/480/360" },
  { id: "class-1", name: "Intro to laser cutting", status: "Available", when: "In 3 days", duration: "One hour", trainingRequired: false, price: 25, image: "https://picsum.photos/seed/class-b/480/360" },
  { id: "class-2", name: "Intro to laser cutting", status: "Available", when: "In 3 days", duration: "One hour", trainingRequired: true,  price: 25, image: "https://picsum.photos/seed/class-c/480/360" },
  { id: "class-3", name: "Intro to laser cutting", status: "Available", when: "In 3 days", duration: "One hour", trainingRequired: false, price: 25, image: "https://picsum.photos/seed/class-d/480/360" },
  { id: "class-4", name: "Intro to laser cutting", status: "Available", when: "In 3 days", duration: "One hour", trainingRequired: false, price: 25, image: "https://picsum.photos/seed/class-e/480/360" },
];


function AutoRow({ children, speed = 22 }) {
  const wrapRef = useRef(null);
  const trackRef = useRef(null);

  useLayoutEffect(() => {
    const wrap = wrapRef.current;
    const track = trackRef.current;
    if (!wrap || !track) return;

    const measure = () => {
      const distance = track.scrollWidth - wrap.clientWidth;
      track.style.setProperty("--distance", `${Math.max(distance, 0)}px`);
      track.style.setProperty("--duration", `${speed}s`);
    };

    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(wrap);
    ro.observe(track);
    return () => ro.disconnect();
  }, [speed]);

  return (
    <div className="auto-row" ref={wrapRef}>
      <div className="auto-row-track" ref={trackRef}>
        {children}
      </div>
    </div>
  );
}

function Pill({ children }) {
  return <span className="pill">{children}</span>;
}

function MachineCard({ item, index }) {
  return (
    <article className="card" data-index={index}>
      <div className="card-image">
        <img src={item.image} alt={item.name} />
        {item.trainingRequired && <span className="badge">Training required</span>}
      </div>
      <div className="card-body">
        <Pill>{item.availability} available</Pill>
        <h3>{item.name}</h3>
        <div className="card-footer">
          <span className="price">${item.price}/hr</span>
          <button className="reserve-btn">Reserve</button>
        </div>
      </div>
    </article>
  );
}

function ClassCard({ item, index }) {
  return (
    <article className="card card-wide" data-index={index}>
      <div className="card-image">
        <img src={item.image} alt={item.name} />
        {item.trainingRequired && <span className="badge">Training required</span>}
      </div>
      <div className="card-body">
        <div className="pill-row">
          <Pill>{item.status}</Pill>
          <Pill>{item.when}</Pill>
          <Pill>{item.duration}</Pill>
        </div>
        <h3>{item.name}</h3>
        <div className="card-footer">
          <span className="price">${item.price}</span>
          <button className="reserve-btn">
            Reserve <span aria-hidden>↗</span>
          </button>
        </div>
      </div>
    </article>
  );
}

export default function MakerspaceCarousel() {
  return (
    <div className="page">
      <style>{`
      
       
        .section {
          margin-bottom: 40px;
        }
        .section-title {
          font-size: 14px;
          font-weight: 500;
          color: #737373;
          margin-bottom: 16px;
        }

       
        .auto-row {
          overflow: hidden;
          width: 100%;
        }
        .auto-row-track {
          display: flex;
          gap: 16px;
          width: max-content;
          animation-name: slide-back-and-forth;
          animation-duration: var(--duration, 20s);
          animation-timing-function: ease-in-out;
          animation-iteration-count: infinite;
          animation-direction: alternate;
        }
        .auto-row:hover .auto-row-track {
          animation-play-state: paused;
        }
        @keyframes slide-back-and-forth {
          from { transform: translateX(0); }
          to   { transform: translateX(calc(-1 * var(--distance, 0px))); }
        }

        .card {
          position: relative;
          flex: 0 0 auto;
          width: 288px;
          border: 1px solid #e5e5e5;
          border-radius: 16px;
          background: #fff;
          overflow: hidden;
        }
        .card-wide {
          width: 320px;
        }
        .card-image {
          position: relative;
          height: 160px;
          width: 100%;
          background: #f0f0f0;
        }
        .card-image img {
          height: 100%;
          width: 100%;
          object-fit: cover;
          display: block;
        }
        .badge {
          position: absolute;
          top: 8px;
          right: 8px;
          background: rgba(255,255,255,0.92);
          padding: 4px 8px;
          border-radius: 6px;
          font-size: 12px;
          color: #404040;
        }
        .card-body {
          padding: 16px;
        }
        .pill-row {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
        }
        .pill {
          display: inline-flex;
          align-items: center;
          border: 1px solid #d4d4d4;
          border-radius: 999px;
          padding: 4px 12px;
          font-size: 12px;
          color: #404040;
          white-space: nowrap;
        }
        .card-body h3 {
          margin: 12px 0 0;
          font-size: 18px;
          font-weight: 500;
          color: #171717;
        }
        .card-footer {
          margin-top: 12px;
          display: flex;
          align-items: center;
          justify-content: space-between;
        }
        .price {
          color: #171717;
        }
        .reserve-btn {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          border: 1px solid #d4d4d4;
          border-radius: 8px;
          padding: 8px 16px;
          font-size: 14px;
          background: #fff;
          color: #262626;
          cursor: pointer;
        }
        .reserve-btn:hover {
          background: #fafafa;
        }
      `}</style>

      <div className="">
        <div className="section">
         
          <AutoRow speed={18}>
            {MACHINES.map((item, index) => (
              <MachineCard key={item.id} item={item} index={index} />
            ))}
          </AutoRow>
        </div>

        <div className="section">
         
          <AutoRow speed={22}>
            {CLASSES.map((item, index) => (
              <ClassCard key={item.id} item={item} index={index} />
            ))}
          </AutoRow>
        </div>
      </div>
    </div>
  );
}