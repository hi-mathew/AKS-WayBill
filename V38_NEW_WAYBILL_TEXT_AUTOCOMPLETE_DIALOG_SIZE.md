# W.A.S.P 1.4.0 - v38 New Waybill autocomplete and dialog sizing

## Changes

- Saved Carrier, Origin / Loading Point, and Destination / Unloading Point ComboBoxes are now **non-editable selection controls**.
- Autocomplete/search is implemented in the corresponding editable text fields below the ComboBoxes, matching the Company selector interaction model.
- Text-field autocomplete supports mouse selection, Down/Up keyboard navigation, Enter selection, and Escape to close.
- Typing a changed carrier name clears the selected carrier master record and clears Driver Name / Vehicle while preserving the typed Carrier Name.
- Selecting a carrier master record populates Carrier Name, Driver Name, and Vehicle / Trailer No.
- Selecting the clear option clears the corresponding populated values.
- Dialog button sizing now uses JavaFX's actual preferred text width instead of a character-count estimate, preventing oversized action buttons.
- Dialog width is calculated from the actual actions, keeping three-button alerts compact while still allowing longer labels such as `Log Out Without Saving` to remain fully visible.
- Added styling for the master-data autocomplete suggestion list.
