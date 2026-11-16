# Phase 14 — Admin-managed homepage slideshow

## What's new
- Admin → Homepage Banners: add, edit, delete, reorder, publish/unpublish slides.
- Book selection loads existing inventory; the selected book cover is the default foreground image.
- Optional custom image URL renders as a banner background when a book is selected, or as the foreground image when no book is selected.
- Editable heading, small label, description, theme, button text and internal destination.
- Slide settings are persisted in the **new** MongoDB `homepage_slides` collection. No existing database documents are removed or changed by the source update.
- Unconfigured installations continue displaying the existing five category slides. After at least one custom slide is created, published custom slides replace the default category slideshow. If all slides are unpublished the slideshow section is hidden.
- Previous Home Settings background banners for the **top hero** are independent and continue working.

## Installation
1. Back up your current frontend, backend, and MongoDB cluster.
2. Apply the backend update and deploy it before the frontend.
3. Apply the frontend update, retaining your existing `.env` and other private configuration.
4. Open **Admin → Homepage Banners**. Choose a book, enter a heading and description, then save.
5. Use ↑ ↓ to reorder or Edit/Delete controls to manage slides.
6. Refresh customer Home to see published slides. Slide rotation is automatic, with arrows and dots on desktop/mobile.

## API routes
- `GET /api/homepage-slides`: public published slides, ordered.
- `GET /api/homepage-slides/configured`: public flag for whether any admin slide exists.
- `GET /api/admin/homepage-slides`: all slides, admin only.
- `POST /api/admin/homepage-slides`: create slide, admin only.
- `PUT /api/admin/homepage-slides/{id}`: update slide, admin only.
- `DELETE /api/admin/homepage-slides/{id}`: delete slide, admin only.
- `PUT /api/admin/homepage-slides/reorder`: `{ "ids": ["...", "..."] }`, all IDs exactly once, admin only.

## Verification status
TypeScript JSX parser syntax checks passed for both modified pages. ZIP integrity checks passed. Full Vite/Maven production builds and live MongoDB integration tests have not been run here; test in staging before production.
