# Phase 16 — Manual Selection Defaults

New homepage section configurations now default to **Manually selected books** for Top Selling, Highly Featured, New Arrivals, Best Rated, and Trending. The default maximum remains 5 books.

Existing MongoDB section configurations are not modified: any previously saved AUTO, FEATURED_FLAG, or NEW_ARRIVAL_FLAG setting remains saved until you deliberately change it in Admin → Home Sections and click Save section. To change an existing section to manual, choose Manually selected books, select books, and save.

Both frontend and backend were updated to use manual selection defaults for newly initialized records. Promotions, wallet, offers, pricing, book data, and all other functionality remain untouched.

Installation: Backup the current source, then copy the respective patch contents to the project root. Deploy backend first, then frontend.

Validation: Archive integrity and static checks passed. Full Maven/Vite builds and live integration tests were not run.
