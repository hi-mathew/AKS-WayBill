## 2.0.0 – Startup / Database Upgrade State Correction

- Added a persistent `data_exchange.schema_version` marker so the Data Exchange identity migration runs only once per database.
- Added a lightweight startup check that reads only the migration marker; normal launches do not rescan or regenerate UUIDs.
- The preparation screen is now shown only when the database is new or requires the 2.0.0 Data Exchange migration.
- Fixed the startup-screen subtitle string so the 2.0.0 source compiles correctly.
- The migration marker is written inside the same transaction as the identity migration, so a failed migration is not marked as complete.

## W.A.S.P. 2.0.0 – Data Exchange Foundation

- Introduced a unique Data Exchange Installation ID for each W.A.S.P. database.
- Added globally unique UUID-based record identities while retaining existing local SQLite IDs.
- Added source-installation tracking to exportable business records.
- Added Data Exchange import tracking structures for future controlled consolidation.
- Existing W.A.S.P. 1.4.0 database data is upgraded in place; existing local IDs and workflows are preserved.
- This release establishes the identity layer for future Full Export, Incremental Export and Central Consolidation features.

## v1.4.0 – PDF DRAFT watermark centering
- Corrected the PDF DRAFT watermark positioning to use the actual rendered glyph bounds and centre the watermark visually on the page, matching the page-centred Word WordArt watermark.
- Removed the previous page-specific positional offset from the PDF watermark calculation.
- Preserved the existing Word watermark and all other report/layout behavior.


## 1.4.0 - Filter Control Alignment
- Standardized list-screen filter control height to 35 px.
- Updated Saved Waybills so the Search field, From/To date pickers, Status selector, Search button and Clear button share the same control height and align consistently.
- Applied the same 35 px search-field sizing to Saved Data, Companies, Audit Log and User Management to keep list-screen filter areas visually consistent.
# Changelog

## W.A.S.P. 1.4.0

### Application and UI

- Added a compact, tabbed Settings screen for waybill numbering, company profile, Terms & Conditions, pagination and backup/restore.
- Improved Dashboard layout, statistics, quick actions and recent-waybill empty state.
- Improved Saved Data tabs and master-data management.
- Added polished User Management and Admin Audit Log screens.
- Improved sidebar branding, footer layout and application navigation.
- Removed unnecessary empty table rows while retaining appropriate scrolling for larger datasets.

### Waybills and master data

- Improved company, carrier and location selection and autocomplete behavior.
- Added master-data creation and editing workflows.
- Added Terms & Conditions editing, double-click edit and reordering.
- Improved New, Edit and View Waybill action bars and validation.

### Reports and exports

- Added PDF and Word report generation improvements.
- Added professionally formatted Excel export for Saved Waybills, including wrapping, borders, filters, freeze panes, print settings and consistent date/time presentation.

### Administration and reliability

- Added SQLite backup and restore support.
- Added Audit Log indexing and pagination improvements.
- Standardized local date/time presentation for user login and audit activity.
- Added configurable pagination sizes.
## v1.4.0 – Item Grid Keyboard Navigation
- Press Enter in the last column of the last item row to add a new item row and focus Description of Goods.
- Press Tab from the last column of the last item row to move focus to Special Instructions / Handling instead of trapping focus in the grid.
- Existing single-click editing, fixed row height, grid borders, and keyboard navigation are retained.

## v1.4.0 – Word final-page rendering fix
- Hardened Word report finalization by removing stale rendered-page-break metadata and ensuring the required trailing paragraph after the Terms & Conditions table is present at minimal height.
- Final Word normalization now runs after all Word report post-processing so later document rewrites cannot reintroduce the blank-page artifacts.
- Retained the 35 px list-screen search/filter controls and Remove Selected item confirmation.

- Added restrained coloured icon badges to Dashboard Quick Actions and Administration cards for improved visual hierarchy while preserving the existing enterprise layout.

## Dashboard icon badge refinement
- Increased Dashboard action icon badges from 25 px to 32 px for improved visibility.
- Increased icon glyph size slightly while retaining the existing restrained colour palette and card layout.
## v1.4.0 – Cumulative dashboard/report fix
- Restored the PDF DRAFT watermark vertical offset so the PDF watermark retains the approved downward positioning relative to the Word report.
- Preserved all prior v1.4.0 fixes, including 35 px list-screen filter controls, Saved Waybills filter alignment, Word final-page handling, Remove Selected item confirmation, item-grid keyboard navigation, and 32 px coloured Dashboard icon badges.
## Pagination Sliding Window Correction
- Fixed pagination ellipsis rendering so the local page window merges with the first/last page anchors when they overlap.
- Clicking a page number now immediately shows adjacent page numbers without an unnecessary ellipsis between pages 2 and 3 (or the corresponding right-side overlap).


## Saved Data grid sizing correction
- Restored dynamic TableView height sizing for Companies, Carriers and Locations so the grid displays only the populated rows on the current page instead of rendering unnecessary blank rows.
- Preserved the existing compact Saved Data layout and scrolling behavior for larger result sets.

## 2.0.0 – Startup / Database Upgrade UX

- Added a visible W.A.S.P. 2.0.0 startup preparation screen before the login screen.
- Moved database initialization and Data Exchange upgrade work off the JavaFX application thread.
- Added live startup status messages for database preparation and Data Exchange identity migration phases.
- Prevented normal application close handling while the startup/database preparation task is running.
- Added a clear startup failure screen/dialog when the database cannot be prepared.
