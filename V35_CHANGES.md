# W.A.S.P 1.4.0 - v35 Changes

## New Waybill / master-data UX fixes
- Fixed CompanySelector so typing into a selected company name no longer clears the company-name editor itself. Dependent company fields are still cleared when the typed name no longer represents the selected master record.
- Added keyboard navigation to company autocomplete suggestions: Down/Up moves through suggestions and Enter selects the highlighted company.
- Made Carrier, Origin / Loading Point, and Destination / Unloading Point saved-data selectors editable and keyboard friendly. Down/Up navigation and Enter selection now work consistently with the company selector.
- The existing clear/select option remains available in all saved-data selectors.

## Dialog polish
- Re-center W.A.S.P-styled JavaFX dialogs after they are shown, relative to the owning application window. This fixes the Waybill Saved dialog appearing slightly off-center.
- Center and space dialog action buttons consistently while retaining the primary blue action treatment for View Waybill.
