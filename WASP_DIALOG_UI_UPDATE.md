# W.A.S.P Dialog UI Update — v24

Implemented application-wide styling for standard JavaFX Alert/DialogPane windows so confirmations, warnings, errors and information dialogs use the W.A.S.P visual language.

## Changes
- White rounded dialog surface with subtle border.
- Light W.A.S.P header area with navy title typography.
- Consistent Segoe UI typography and spacing.
- W.A.S.P blue primary/default action button.
- Neutral secondary/cancel buttons.
- Hover/focus states consistent with the application.
- Replaced the stock JavaFX alert graphic with a compact W.A.S.P-styled badge for question/info/warning/error dialogs.
- Styling is applied centrally from `Main` to dialogs as they are created, so existing Alert usages inherit the new appearance without changing application behavior.
- Existing dialog titles, messages, button semantics and confirmation logic are preserved.
