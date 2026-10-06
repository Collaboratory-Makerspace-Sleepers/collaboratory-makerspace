# Frontend

React + Vite single-page app for the Collaboratory makerspace platform. Consumes the Spring Boot REST API in [`../backend`](../backend).

## Stack

React 19, Vite 7, React Router 7, Tailwind CSS 4.

## Setup

```bash
npm install
npm run dev        # dev server with HMR
```

The dev server expects the backend running locally on port 8080. Set the API base URL in `.env.local`:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

## Scripts

| Command | Purpose |
| ------- | ------- |
| `npm run dev` | Start the dev server |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | ESLint across the project |

## Structure

Routes are defined in `src/`, with API calls hitting the backend under `/api`. Auth runs through the backend's Auth0 redirect flow; the JWT cookie issued at login is exchanged for a Bearer token on API requests.
