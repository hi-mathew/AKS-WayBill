package com.aks.waybill.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Common pagination control used by paginated list screens.
 * Page indexes exposed to the loader are zero-based; the UI is one-based.
 */
public final class PaginationControl extends VBox {
    private final Label resultInfo = new Label();
    private final Label pageInfo = new Label();
    private final HBox pageButtons = new HBox(5);
    private final TextField goToPage = new TextField();
    private final Button goButton = new Button("Go");
    private final Button firstButton = new Button("« First");
    private final Button previousButton = new Button("‹ Previous");
    private final Button nextButton = new Button("Next ›");
    private final Button lastButton = new Button("Last »");

    private IntConsumer pageLoader = page -> {};
    private int currentPage;
    private int totalPages = 1;

    public PaginationControl() {
        super(7);
        setFillWidth(true);
        setPadding(new Insets(2, 0, 0, 0));

        resultInfo.getStyleClass().addAll("pagination-info", "card-description");
        pageInfo.getStyleClass().addAll("pagination-page-info", "card-description");

        for (Button button : new Button[]{firstButton, previousButton, nextButton, lastButton}) {
            button.getStyleClass().add("secondary-button");
        }
        firstButton.setOnAction(e -> navigate(0));
        previousButton.setOnAction(e -> navigate(currentPage - 1));
        nextButton.setOnAction(e -> navigate(currentPage + 1));
        lastButton.setOnAction(e -> navigate(totalPages - 1));

        pageButtons.setAlignment(Pos.CENTER);
        pageButtons.setFillHeight(false);

        goToPage.setPromptText("Page");
        goToPage.setPrefWidth(58);
        goToPage.setMinWidth(58);
        goToPage.setMaxWidth(58);
        goToPage.getStyleClass().add("pagination-go-field");
        goToPage.setOnAction(e -> goToEnteredPage());
        goButton.getStyleClass().add("secondary-button");
        goButton.setOnAction(e -> goToEnteredPage());

        Label goLabel = new Label("Go to page:");
        goLabel.getStyleClass().add("pagination-go-label");
        HBox goBox = new HBox(6, goLabel, goToPage, goButton);
        goBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacerLeft = new Region();
        Region spacerRight = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);
        HBox.setHgrow(spacerRight, Priority.ALWAYS);
        HBox infoRow = new HBox(10, resultInfo, spacerLeft, pageInfo, spacerRight, goBox);
        infoRow.setAlignment(Pos.CENTER_LEFT);

        HBox navigationRow = new HBox(7, firstButton, previousButton, pageButtons, nextButton, lastButton);
        navigationRow.setAlignment(Pos.CENTER);

        getChildren().addAll(infoRow, navigationRow);
        updateControls();
    }

    public void setPageLoader(IntConsumer loader) {
        this.pageLoader = loader == null ? page -> {} : loader;
    }

    /**
     * Updates the displayed pagination state. currentPage is zero-based.
     */
    public void setPageData(int currentPage, int totalPages, long totalRows, int pageSize) {
        this.totalPages = Math.max(1, totalPages);
        this.currentPage = Math.max(0, Math.min(currentPage, this.totalPages - 1));

        int size = Math.max(1, pageSize);
        long start = totalRows == 0 ? 0 : (long) this.currentPage * size + 1;
        long end = Math.min(totalRows, (long) (this.currentPage + 1) * size);
        resultInfo.setText("Showing " + start + "–" + end + " of " + totalRows);
        pageInfo.setText("Page " + (this.currentPage + 1) + " of " + this.totalPages);
        goToPage.setPromptText("1–" + this.totalPages);
        goToPage.setText(String.valueOf(this.currentPage + 1));
        goToPage.selectAll();
        clearGoToError();
        buildPageButtons();
        updateControls();
    }

    private void navigate(int page) {
        if (page < 0 || page >= totalPages || page == currentPage) return;
        pageLoader.accept(page);
    }

    private void goToEnteredPage() {
        String value = goToPage.getText() == null ? "" : goToPage.getText().trim();
        try {
            int page = Integer.parseInt(value);
            if (page < 1 || page > totalPages) {
                markGoToError();
                return;
            }
            clearGoToError();
            navigate(page - 1);
        } catch (NumberFormatException ex) {
            markGoToError();
        }
    }

    private void buildPageButtons() {
        pageButtons.getChildren().clear();
        List<Integer> pages = visiblePages();
        Integer previousPage = null;
        for (Integer page : pages) {
            if (page == null) {
                Label ellipsis = new Label("…");
                ellipsis.getStyleClass().add("pagination-ellipsis");
                pageButtons.getChildren().add(ellipsis);
                continue;
            }
            if (previousPage != null && page == previousPage) continue;
            Button button = new Button(String.valueOf(page + 1));
            button.getStyleClass().add(page == currentPage ? "nav-selected" : "secondary-button");
            button.setMinWidth(38);
            button.setOnAction(e -> navigate(page));
            pageButtons.getChildren().add(button);
            previousPage = page;
        }
    }

    private List<Integer> visiblePages() {
        List<Integer> result = new ArrayList<>();
        if (totalPages <= 11) {
            for (int i = 0; i < totalPages; i++) result.add(i);
            return result;
        }

        // Keep the first/last pages as anchors and build a local window
        // around the current page. Merge overlapping ranges so that an
        // unnecessary leading/trailing ellipsis is never displayed.
        java.util.SortedSet<Integer> visible = new java.util.TreeSet<>();
        visible.add(0);
        visible.add(1);
        visible.add(totalPages - 2);
        visible.add(totalPages - 1);

        int localStart = Math.max(0, currentPage - 2);
        int localEnd = Math.min(totalPages - 1, currentPage + 2);
        for (int i = localStart; i <= localEnd; i++) visible.add(i);

        Integer previous = null;
        for (Integer page : visible) {
            if (previous != null && page - previous > 1) result.add(null);
            result.add(page);
            previous = page;
        }
        return result;
    }

    private void updateControls() {
        firstButton.setDisable(currentPage <= 0);
        previousButton.setDisable(currentPage <= 0);
        nextButton.setDisable(currentPage >= totalPages - 1);
        lastButton.setDisable(currentPage >= totalPages - 1);
    }

    private void markGoToError() {
        if (!goToPage.getStyleClass().contains("pagination-go-error")) {
            goToPage.getStyleClass().add("pagination-go-error");
        }
        goToPage.setTooltip(new javafx.scene.control.Tooltip("Enter a page number from 1 to " + totalPages + "."));
        goToPage.requestFocus();
        goToPage.selectAll();
    }

    private void clearGoToError() {
        goToPage.getStyleClass().remove("pagination-go-error");
        goToPage.setTooltip(null);
    }
}
