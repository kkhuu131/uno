# UNO Frontend

Vite + React + TypeScript hot-seat client for the UNO backend.

## Prerequisites

- Node 18+
- Backend running on `http://localhost:8080`

## Install & run

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

## Build

```bash
npm run build      # output in dist/
npm run preview    # preview the build locally
```

## Configuration

Override the API base URL with a `.env.local` file:

```
VITE_API_BASE=http://localhost:8080/api
```
