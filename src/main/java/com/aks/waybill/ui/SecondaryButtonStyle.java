package com.aks.waybill.ui;

import javafx.scene.control.Button;

/** Applies explicit JavaFX inline styles so secondary-button borders and hover feedback
 * remain visible even when a view's stylesheet selector is overridden. */
public final class SecondaryButtonStyle {
    private SecondaryButtonStyle() {}

    private static final String NORMAL = "-fx-background-color: #f7fafc;"
            + "-fx-text-fill: #164f7a;"
            + "-fx-border-color: #86a5bd; -fx-border-width: 1px; -fx-border-style: solid;"
            + "-fx-border-radius: 7px; -fx-border-insets: 0;"
            + "-fx-background-radius: 7px; -fx-background-insets: 0; -fx-cursor: hand;";
    private static final String HOVER = "-fx-background-color: #e5eff6;"
            + "-fx-text-fill: #164f7a;"
            + "-fx-border-color: #5f8aa9; -fx-border-width: 1.2px; -fx-border-style: solid;"
            + "-fx-border-radius: 7px; -fx-border-insets: 0;"
            + "-fx-background-radius: 7px; -fx-background-insets: 0; -fx-cursor: hand;";
    private static final String FOCUSED = "-fx-background-color: #f7fafc;"
            + "-fx-text-fill: #164f7a;"
            + "-fx-border-color: #397da8; -fx-border-width: 1.5px; -fx-border-style: solid;"
            + "-fx-border-radius: 7px; -fx-border-insets: 0;"
            + "-fx-background-radius: 7px; -fx-background-insets: 0; -fx-cursor: hand;";
    private static final String PRESSED = "-fx-background-color: #d7e6f0;"
            + "-fx-text-fill: #164f7a;"
            + "-fx-border-color: #4f7e9f; -fx-border-width: 1px; -fx-border-style: solid;"
            + "-fx-border-radius: 7px; -fx-border-insets: 0;"
            + "-fx-background-radius: 7px; -fx-background-insets: 0; -fx-cursor: hand;";

    public static void apply(Button button) {
        button.setStyle(NORMAL);
        Runnable update = () -> {
            if (button.isDisabled()) {
                button.setStyle(NORMAL + "-fx-opacity: 0.62;");
            } else if (button.isPressed()) {
                button.setStyle(PRESSED);
            } else if (button.isFocused()) {
                button.setStyle(button.isHover() ? HOVER : FOCUSED);
            } else if (button.isHover()) {
                button.setStyle(HOVER);
            } else {
                button.setStyle(NORMAL);
            }
        };
        button.hoverProperty().addListener((obs, oldValue, newValue) -> update.run());
        button.pressedProperty().addListener((obs, oldValue, newValue) -> update.run());
        button.focusedProperty().addListener((obs, oldValue, newValue) -> update.run());
        button.disabledProperty().addListener((obs, oldValue, newValue) -> update.run());
    }
}
