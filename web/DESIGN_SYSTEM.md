# RapidPhotoUpload Design System

This document defines the design language and component patterns used throughout the application.

## Design Principles

1. **Clean & Modern** - Inspired by Google Photos with smooth animations and card-based layouts
2. **Performance First** - Skeleton loading states instead of spinners, lazy loading images
3. **Feedback Rich** - Every interaction provides visual feedback (hover, active, loading states)
4. **Accessible** - Semantic HTML, keyboard navigation, focus states

## Color Palette

### Primary Colors
```css
--primary-color: #3b82f6;    /* Blue - primary actions */
--primary-hover: #2563eb;    /* Darker blue - hover state */
```

### Semantic Colors
```css
--success-color: #10b981;    /* Green - success states */
--error-color: #ef4444;      /* Red - errors, delete actions */
--warning-color: #f59e0b;    /* Orange - warnings, pending states */
--secondary-color: #6b7280;  /* Gray - secondary text */
```

### Backgrounds
```css
--bg-primary: #ffffff;       /* White - cards, modals */
--bg-secondary: #f9fafb;     /* Light gray - page background */
--bg-tertiary: #f3f4f6;      /* Medium gray - skeleton, placeholders */
```

### Text
```css
--text-primary: #111827;     /* Dark gray - headings, body text */
--text-secondary: #6b7280;   /* Medium gray - metadata, labels */
```

### Borders & Shadows
```css
--border-color: #e5e7eb;     /* Light gray - borders */
--shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
--shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
--shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
```

## Typography

### Font Family
```css
font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
  'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;
```

### Scale
- **Base:** 16px (1rem)
- **Line Height:** 1.5
- **Headings:** 2rem - 3.2rem
- **Body:** 1rem
- **Small:** 0.875rem (14px)
- **Tiny:** 0.75rem (12px)

## Spacing

Uses rem-based spacing for consistency:
- **xs:** 0.25rem (4px)
- **sm:** 0.5rem (8px)
- **md:** 1rem (16px)
- **lg:** 1.5rem (24px)
- **xl:** 2rem (32px)
- **2xl:** 3rem (48px)

## Border Radius

```css
--radius: 8px;  /* Standard radius for cards, buttons */
```

Special cases:
- **Rounded:** 999px (pills, tags)
- **Large:** 12px (modals)
- **Circle:** 50% (close button, avatars)

## Component Patterns

### Buttons

**Variants:**
- `.btn-primary` - Main actions (blue)
- `.btn-secondary` - Secondary actions (gray)
- `.btn-danger` - Destructive actions (red)

**Sizes:**
- `.btn-small` - Compact (0.375rem padding)
- Default - Standard (0.625rem padding)
- `.btn-large` - Prominent (0.875rem padding)

**States:**
```css
/* Hover - lift up, enhance shadow */
transform: translateY(-1px);
box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);

/* Active - press down */
transform: translateY(0);
box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);

/* Disabled - reduce opacity */
opacity: 0.6;
cursor: not-allowed;
```

### Cards

**Photo Cards:**
- Aspect ratio: 4/3
- Border radius: 8px
- Shadow: sm → lg on hover
- Hover: lift (-8px) + scale (1.02)
- Image zoom: 1.1x scale on hover
- Transition: 0.3s cubic-bezier(0.4, 0, 0.2, 1)

**Animation:**
```css
/* Fade in + slide up */
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
```

**Staggered Delays:**
- Apply delay based on index: `index % 12 * 0.05s`
- Creates cascading reveal effect

### Modal

**Layout:**
- Fixed overlay with backdrop blur
- Max width: 1200px
- Max height: 90vh
- Border radius: 12px (larger than standard)
- Shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25)

**Animations:**
```css
/* Overlay fade in */
@keyframes fadeInOverlay {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* Content slide up */
@keyframes slideUp {
  from { opacity: 0; transform: translateY(40px) scale(0.95); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
```

**Close Button:**
- Circular, semi-transparent background
- Backdrop blur
- Rotates 90° on hover
- Size: 40px x 40px

### Loading States

**Skeleton Shimmer:**
```css
background: linear-gradient(
  90deg,
  var(--bg-tertiary) 0%,
  var(--bg-secondary) 50%,
  var(--bg-tertiary) 100%
);
background-size: 200% 100%;
animation: shimmer 1.5s infinite;
```

**Pattern:**
1. Show skeleton immediately
2. Load content in background
3. Fade in when ready (opacity 0 → 1, 300ms)

### Infinite Scroll

**Intersection Observer:**
- Attach to last photo element
- Load threshold: when visible
- Page size: 100 items
- Loading indicator: 8 skeleton cards

**End State:**
- Gray text: "You've reached the end"
- Center aligned
- Padding: 3rem

## Grid System

**Gallery Grid:**
```css
display: grid;
grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
gap: 1.5rem;
```

**Responsive:**
- Mobile (<768px): minmax(200px, 1fr)
- Desktop: minmax(280px, 1fr)

## Animation Timing

**Standard Easing:**
```css
cubic-bezier(0.4, 0, 0.2, 1)  /* Ease out */
```

**Durations:**
- Quick: 0.2s (overlays, tooltips)
- Standard: 0.3s (hovers, transitions)
- Slow: 0.4s (page transitions)
- Continuous: 1.5s (shimmer animation)

## Accessibility

**Focus States:**
```css
outline: 2px solid var(--primary-color);
outline-offset: 2px;
```

**Hover States:**
- All interactive elements have visible hover feedback
- Cursor changes to pointer
- Visual lift or shadow enhancement

**Color Contrast:**
- Text on background: WCAG AA compliant
- Buttons use sufficient contrast ratios

## Usage Guidelines

### DO:
✅ Use CSS variables for all colors
✅ Apply staggered animations to lists
✅ Show skeleton loaders before content
✅ Provide hover feedback on all clickable elements
✅ Use semantic HTML (button, nav, header)
✅ Maintain consistent spacing (multiples of 0.5rem)

### DON'T:
❌ Use hardcoded colors
❌ Show "Loading..." text without context
❌ Mix animation timings arbitrarily
❌ Forget disabled/error states
❌ Use different border radius values
❌ Add emojis unless explicitly requested

## Component Checklist

When creating a new component, ensure it has:
- [ ] Hover state (if interactive)
- [ ] Active/pressed state (if button)
- [ ] Disabled state (if applicable)
- [ ] Loading state (if async)
- [ ] Error state (if can fail)
- [ ] Smooth transitions (0.2s - 0.4s)
- [ ] Uses CSS variables for colors
- [ ] Proper spacing (rem-based)
- [ ] Accessible focus outline

## File Organization

```
web/src/
├── index.css              # All styles (design system + components)
├── components/
│   ├── Auth/             # Login, Register, ProtectedRoute
│   ├── Layout/           # Header
│   ├── PhotoGallery/     # PhotoGallery, PhotoCard, PhotoModal, PhotoSkeleton
│   └── PhotoUploader/    # PhotoUploader, FileSelector, UploadProgress, UploadQueue
├── hooks/                # usePhotos, useAuth
├── services/             # API clients
└── types/                # TypeScript interfaces
```

---

**Last Updated:** 2025-01-08
**Maintained By:** Claude Code
