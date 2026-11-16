# Phase 17 — Homepage slideshow fixes

## Changes
- Prevent old category carousel flashing while the server checks managed slide configuration.
- Keep existing category slides as fallback only when no admin slides have been configured.
- Set autoplay to 5.5 seconds; pause on mouse hover or keyboard focus, and reset the interval after manual navigation.
- Correct saved `/book/BOOK_ID` placeholders and generate a real `/book/<selected-book-id>` URL when choosing a book. The existing MongoDB document is not rewritten; legacy links are resolved when returned by the API.
- Replace native featured-book selector with a searchable book chooser, and native theme selector with a styled dropdown.
- Improve customer slideshow styling and admin live preview, including responsive layouts.

## Install
Back up both projects. Apply the backend patch and deploy backend first; then apply frontend patch. Preserve all files not mentioned in the patches and your existing environment files. No MongoDB reset or migration is needed.

## Verify
1. Hard-refresh `/home`: no older category slide appears while saved slides load.
2. Add a published slide, select a featured book, and verify the generated button link is `/book/<real-id>`.
3. Open its action from `/home` and confirm the correct book page appears.
4. Check Next, Previous and slide indicator controls, and auto-advance after about 5.5 seconds.
5. Test hidden slides, reordered slides and mobile width.

Note: Full Maven, Vite and live deployment tests were not run here. If the homepage-slide API is unreachable the original category fallback appears after the request fails.
