package com.aks.waybill.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
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
    /**
     * Sizes a TableView to the number of populated rows, up to a practical maximum.
     * This avoids displaying empty placeholder rows while still allowing scrolling
     * when a non-paginated list contains more than the visible-row limit.
     */
    protected static void fitTableHeight(TableView<?> table, int itemCount, int maxVisibleRows, double cellHeight) {
        int visibleRows = Math.max(1, Math.min(itemCount, maxVisibleRows));
        // The actual JavaFX header is slightly taller than the nominal cell
        // height once the W.A.S.P. table CSS (padding/borders/header sizing) is
        // applied. Give the table a small safety margin so a fully visible page
        // does not unnecessarily create a vertical scrollbar.
        double headerHeight = 34.0;
        double height = headerHeight + (visibleRows * cellHeight) + 10.0;
        table.setFixedCellSize(cellHeight);
        // Size the table to the actual page contents. If the available window
        // height is smaller, JavaFX can shrink the table and TableView itself
        // provides the record scrollbar. The action/paging controls therefore
        // remain outside the scrolling record area and empty rows are avoided.
        table.setMinHeight(Math.max(72.0, Math.min(height, headerHeight + cellHeight + 4.0)));
        table.setPrefHeight(height);
        table.setMaxHeight(height);
        table.getStyleClass().remove("fit-no-scroll");
        VBox.setVgrow(table, javafx.scene.layout.Priority.NEVER);
    }

}
