# W.A.S.P 1.4.0 – v40 Dialog and Master Autocomplete Fix

## Fixed

- Stabilised all W.A.S.P dialog button dimensions using final inline sizing so JavaFX CSS pseudo-classes cannot shrink or move buttons on mouse-over/focus.
- Increased widths for long action labels so labels such as `Continue Without Backup`, `Log Out Without Saving`, `Save and Continue`, and `Leave Without Saving` remain fully visible.
- Dialog width is now calculated from the actual button set with a safe minimum/maximum.
- Removed the conflicting stylesheet minimum width that could override the runtime button dimensions.
- Kept a constant one-pixel button border so focused/hovered states do not change button geometry.
- Fixed Carrier, Origin and Destination text-field autocomplete: the suggestion `ListView`s are now actually attached to their `Popup`s.
- Added popup auto-hide, escape handling and automatic screen-boundary correction.
- Preserved the intended behaviour that master-data ComboBoxes remain non-editable; autocomplete is available only in the editable text field below each selector, matching CompanySelector.

## Expected behaviour

1. Select a Carrier/Location from its ComboBox: master data populates the corresponding fields.
2. Type into the text field: matching master values appear below the field.
3. Press Down/Up to navigate suggestions and Enter to select one.
4. Modify a selected master value: the saved-record association is cleared, but the user's typed value remains.
5. Selecting the clear option in a master ComboBox clears the populated fields.
6. Dialog buttons remain the same size while hovering/focusing and their labels are not truncated.
