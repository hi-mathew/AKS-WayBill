
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
