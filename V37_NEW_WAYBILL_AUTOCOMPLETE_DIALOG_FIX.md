# W.A.S.P 1.4.0 – v37 New Waybill and Dialog Fixes

## Changes

### Unsaved Changes dialog
- Dialog action buttons are now sized from their actual labels so labels such as `Exit Without Saving`, `Save and Exit`, and `Save and Log Out` are not truncated.
- Existing W.A.S.P dialog styling, centering and icon positioning are retained.

### Carrier master data
- Carrier selector now supports type-ahead filtering.
- Up/Down arrow navigation works through matching carrier values.
- Enter selects the highlighted/exact carrier.
- Selecting a carrier populates Carrier Name, Driver Name and Vehicle / Trailer No.
- The explicit `— Select saved carrier —` option remains available and clears the populated carrier fields.

### Location master data
- Origin / Loading Point and Destination / Unloading Point now support the same type-ahead behaviour.
- Up/Down arrow navigation and Enter selection are supported.
- Selecting a location populates the corresponding location field.
- The explicit `— Select or type below —` option remains available and clears the corresponding field.

### Existing behaviour retained
- Company master-data autocomplete and keyboard selection are unchanged.
- Historical waybill values remain independent snapshots from master data.
- Dialog visual styling and centering changes from previous versions are retained.
