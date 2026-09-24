# W.A.S.P 1.4.0 v42 – Dialog Layout and Carrier Autocomplete Fix

## Fixes

### 1. Generic dialog sizing
- Reworked the shared W.A.S.P DialogPane sizing policy in `Main.java`.
- Button widths are measured from their actual rendered labels.
- Button min/pref/max width and height are fixed together so hover/focus states cannot resize them.
- The DialogPane, ButtonBar, ButtonBar container, and decorated Stage now receive a consistent calculated width.
- Additional layout breathing room is reserved for the JavaFX ButtonBar skin so the last button cannot be clipped at the right edge.
- The policy is shared by standard JavaFX alerts/dialogs rather than being implemented per alert.
- Existing W.A.S.P colors and hover/focus visual states remain controlled by CSS.

### 2. Carrier autocomplete selection
- Fixed the autocomplete selection flow in `WaybillFormView`.
- Previously the ComboBox selection and `onSelected` callback were executed while `applyingMasterSelection` was true.
- `applyCarrierSelection()` therefore returned immediately and did not populate Driver Name and Vehicle / Trailer No.
- The selector is now changed under the guard, the guard is released, and the real master-selection callback is then executed.
- The fix applies to both keyboard Enter selection and mouse selection.
- Existing direct ComboBox selection behaviour is preserved.

## Expected Carrier behaviour

Selecting a saved carrier from the ComboBox:
- populates Carrier Name
- populates Driver Name
- populates Vehicle / Trailer No.

Typing into Carrier Name:
- keeps the typed text
- clears the saved Carrier association
- clears dependent Driver / Vehicle values
- shows matching autocomplete suggestions

Selecting a carrier from autocomplete:
- updates the non-editable saved Carrier selector
- populates Carrier Name
- populates Driver Name
- populates Vehicle / Trailer No.
