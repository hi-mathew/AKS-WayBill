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
        // Let the parent layout grow the table when there is spare room, but
        // allow it to shrink below its preferred row count when the window is
        // smaller. In that case TableView itself provides the record scrollbar.
        // This keeps action/paging controls outside the scrolling record area.
        table.setMinHeight(Math.max(72.0, headerHeight + cellHeight + 4.0));
        table.setPrefHeight(height);
        table.setMaxHeight(Double.MAX_VALUE);
        table.getStyleClass().remove("fit-no-scroll");
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
    }

}
