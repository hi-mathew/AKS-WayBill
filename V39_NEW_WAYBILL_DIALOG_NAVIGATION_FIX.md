# W.A.S.P 1.4.0 – v39 Changes

## New Waybill master-data selectors
- Kept Carrier, Origin and Destination master-data ComboBoxes non-editable and selection-only.
- Added an internal master-selection guard modelled after CompanySelector's internal-change pattern.
- Selecting Carrier from the master now reliably populates Carrier Name, Driver Name and Vehicle/Trailer No.
- Selecting Origin/Destination reliably populates the corresponding text field.
- Typing manually into Carrier/Location text fields clears the master selection without clearing the text being typed.
- Carrier manual edits still clear Driver Name and Vehicle/Trailer No., while preserving the edited Carrier Name.
- Text autocomplete continues to operate on the editable text fields below the master selectors, with Up/Down/Enter/Escape and mouse selection.

## Dialog buttons
- Dialog button widths are now deterministic based on the actual action label length.
- Button width is reapplied whenever DialogPane button types change, fixing the timing issue where JavaFX measured buttons before their labels existed.
- Fixed button heights prevent hover/focus states from appearing to resize or move the buttons.
- Long labels such as "Log Out Without Saving", "Save and Log Out", "Continue Without Backup" and "Leave Without Saving" remain fully visible.

## Unsaved changes navigation protection
- New/Edit Waybill dirty-state protection now applies to all in-application navigation:
  - Dashboard
  - New Waybill
  - Saved Waybills
  - Saved Data
  - Settings
  - User Management
  - View/Edit Waybill navigation
- The navigation prompt offers Save and Continue, Leave Without Saving, or Cancel.
- Logout/Exit protection remains separate and continues to include the backup reminder after the unsaved-change decision.
