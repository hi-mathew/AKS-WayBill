package com.aks.waybill.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Temporary functional content for modules whose business screens are implemented next. */
public class PlaceholderView extends AppView {
    public PlaceholderView(String title, String subtitle, String message) {
        super(title, subtitle, card(message));
    }

    private static VBox card(String message) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(24));
        card.getStyleClass().add("empty-state");
        Label text = new Label(message);
        text.setWrapText(true);
        card.getChildren().add(text);
        return card;
    }
}
