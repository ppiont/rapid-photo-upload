# RapidPhotoUpload Web Client

React + TypeScript web client for the RapidPhotoUpload system.

## Features

- User authentication (register/login with JWT)
- Concurrent photo uploads (up to 100 at once)
- Real-time upload progress tracking
- Photo gallery with pagination
- Photo tagging functionality
- Responsive design for mobile and desktop

## Tech Stack

- React 19
- TypeScript
- Vite
- React Router
- Axios
- CSS3 with responsive design

## Getting Started

### Prerequisites

- Node.js 18+ or Bun
- Backend API running on `http://localhost:8080`

### Installation

```bash
# Install dependencies
bun install
# or
npm install
```

### Configuration

Create a `.env` file (see `.env.example`):

```env
VITE_API_BASE_URL=http://localhost:8080
```

### Development

```bash
# Start development server with hot reload
bun dev
# or
npm run dev
```

The app will be available at `http://localhost:3000`.

### Build for Production

```bash
# Build optimized production bundle
bun run build
# or
npm run build
```

### Preview Production Build

```bash
# Preview production build locally
bun run preview
# or
npm run preview
```

## Project Structure

```
src/
├── components/          # React components
│   ├── Auth/           # Authentication components
│   ├── Layout/         # Layout components (Header, etc.)
│   ├── PhotoUploader/  # Upload functionality components
│   └── PhotoGallery/   # Gallery and photo viewing components
├── contexts/           # React contexts (Auth)
├── hooks/              # Custom React hooks
├── services/           # API service layer
├── types/              # TypeScript type definitions
├── App.tsx             # Main app component with routing
├── main.tsx            # Application entry point
└── index.css           # Global styles

## Key Features

### Concurrent Uploads

The app supports uploading up to 100 photos simultaneously using:
- Direct S3 uploads via pre-signed URLs
- Parallel Promise.all execution
- Real-time progress tracking with Axios onUploadProgress

### State Management

- Auth state managed with React Context
- Local component state for uploads and gallery
- JWT token stored in localStorage

### Upload Flow

1. User selects photos (up to 100)
2. Client calls `/api/upload/initialize` with file metadata
3. Backend returns pre-signed S3 URLs
4. Client uploads directly to S3 in parallel
5. Client notifies backend of completion/failure
6. Real-time progress updates in UI

### Responsive Design

- Mobile-first CSS with media queries
- Breakpoints: 768px
- Touch-friendly UI elements
- Responsive grid layout

## Available Scripts

- `bun dev` / `npm run dev` - Start development server
- `bun run build` / `npm run build` - Build for production
- `bun run preview` / `npm run preview` - Preview production build

## Environment Variables

- `VITE_API_BASE_URL` - Backend API base URL (default: `http://localhost:8080`)

## Browser Support

- Modern browsers (Chrome, Firefox, Safari, Edge)
- ES2022+ features required
- No IE11 support
