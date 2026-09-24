# W.A.S.P 1.4.0 - V41 Dialog Sizing and Master Autocomplete Fix

## Fixes

- Fixed Carrier text editing after selecting a saved Carrier. Programmatic reset of the selector to the clear option no longer triggers the action handler that clears the text field.
- Fixed Origin and Destination text editing with the same protection.
- Preserved dependent Carrier fields (Driver Name and Vehicle/Trailer No.) clearing when the Carrier text is manually changed, while preserving the modified Carrier text itself.
- Centralized dialog button sizing around actual rendered label width.
- Removed conflicting hard-coded dialog widths from the logout/exit and backup reminder flows.
- Dialog buttons now have stable min/pref/max width and height, including hover and focus states.
- Dialog width is calculated from all action labels, so long labels such as `Continue Without Backup`, `Log Out Without Saving`, and `Save and Exit` remain fully visible.
