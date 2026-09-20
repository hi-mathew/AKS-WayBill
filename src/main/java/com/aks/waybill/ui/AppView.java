package com.aks.waybill.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class AppView extends VBox {

    public AppView(String title, String subtitle) {
        setPadding(new Insets(30));
        setSpacing(20);
        getStyleClass().add("content-area");

        Label heading = new Label(title);
        heading.getStyleClass().add("page-heading");

        Label description = new Label(subtitle);
        description.getStyleClass().add("page-subheading");
        description.setWrapText(true);

        getChildren().addAll(heading, description);
    }

    public AppView(String title, String subtitle, Node content) {
        this(title, subtitle);
        getChildren().add(content);
    }
}
