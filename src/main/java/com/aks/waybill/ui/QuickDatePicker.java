package com.aks.waybill.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.skin.DatePickerSkin;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * W.A.S.P. DatePicker with quick Month/Year selection and a visible day calendar.
 *
 * The standard JavaFX DatePicker editor remains available for direct date entry.
 * The popup provides:
 *  - Month selector
 *  - Editable year spinner (1900-2100)
 *  - Previous/next month navigation
 *  - Clickable day calendar
 */
public final class QuickDatePicker extends DatePicker {
    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2100;

    public QuickDatePicker() {
        super();
        getStyleClass().add("quick-date-picker");
    }

    public QuickDatePicker(LocalDate value) {
        super(value);
        getStyleClass().add("quick-date-picker");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DatePickerSkin(this) {
            @Override
            public Node getPopupContent() {
                /*
                 * DatePickerSkin.show() calls datePickerContent.clearFocus()
                 * after super.show(). The JavaFX 21 skin initializes its
                 * internal datePickerContent from getPopupContent(). Since we
                 * provide our own popup, we still need to let the standard
                 * implementation initialize that internal content first.
                 *
                 * Without this call, opening the picker can result in:
                 *   NullPointerException: datePickerContent is null
                 * in DatePickerSkin.show().
                 */
                super.getPopupContent();
                return createCalendarPopup();
            }
        };
    }

    private Node createCalendarPopup() {
        LocalDate initialDate = getValue() == null ? LocalDate.now() : getValue();
        final YearMonth[] displayedMonth = {YearMonth.from(initialDate)};

        ComboBox<Month> monthSelector = new ComboBox<>();
        monthSelector.setItems(FXCollections.observableArrayList(Month.values()));
        monthSelector.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Month month, boolean empty) {
                super.updateItem(month, empty);
                setText(empty || month == null ? null
                        : month.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
            }
        });
        monthSelector.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Month month, boolean empty) {
                super.updateItem(month, empty);
                setText(empty || month == null ? null
                        : month.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
            }
        });
        monthSelector.setValue(initialDate.getMonth());
        monthSelector.setPrefWidth(120);
        monthSelector.setMinWidth(120);
        monthSelector.setMaxWidth(120);

        Spinner<Integer> yearSpinner = new Spinner<>();
        yearSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                MIN_YEAR, MAX_YEAR, initialDate.getYear()));
        yearSpinner.setEditable(true);
        yearSpinner.setPrefWidth(82);
        yearSpinner.setMinWidth(82);
        yearSpinner.setMaxWidth(82);
        yearSpinner.getStyleClass().add("quick-date-year-spinner");

        TextField yearEditor = yearSpinner.getEditor();
        yearEditor.setPromptText("Year");
        yearEditor.setAlignment(Pos.CENTER_LEFT);

        Label monthLabel = new Label("Month");
        monthLabel.getStyleClass().add("quick-date-selector-label");
        Label yearLabel = new Label("Year");
        yearLabel.getStyleClass().add("quick-date-selector-label");

        HBox monthBox = new HBox(4, monthLabel, monthSelector);
        monthBox.setAlignment(Pos.CENTER_LEFT);
        HBox yearBox = new HBox(4, yearLabel, yearSpinner);
        yearBox.setAlignment(Pos.CENTER_LEFT);

        Button previousMonth = new Button("‹");
        previousMonth.getStyleClass().add("quick-date-nav");
        previousMonth.setMinSize(28, 26);
        previousMonth.setPrefSize(28, 26);

        Button nextMonth = new Button("›");
        nextMonth.getStyleClass().add("quick-date-nav");
        nextMonth.setMinSize(28, 26);
        nextMonth.setPrefSize(28, 26);

        HBox selectors = new HBox(8, monthBox, yearBox);
        selectors.setAlignment(Pos.CENTER_LEFT);
        selectors.setPadding(new Insets(8, 10, 5, 10));
        selectors.getStyleClass().add("quick-date-selectors");

        HBox navigation = new HBox(5, previousMonth, nextMonth);
        navigation.setAlignment(Pos.CENTER);
        navigation.setPadding(new Insets(2, 10, 5, 10));
        navigation.getStyleClass().add("quick-date-navigation");

        GridPane calendarGrid = new GridPane();
        calendarGrid.setHgap(2);
        calendarGrid.setVgap(2);
        calendarGrid.setPadding(new Insets(5, 10, 8, 10));
        calendarGrid.setAlignment(Pos.CENTER);

        Runnable refreshCalendar = () -> {
            YearMonth ym = displayedMonth[0];
            monthSelector.setValue(ym.getMonth());
            SpinnerValueFactory.IntegerSpinnerValueFactory vf =
                    (SpinnerValueFactory.IntegerSpinnerValueFactory) yearSpinner.getValueFactory();
            if (vf.getValue() != ym.getYear()) {
                vf.setValue(ym.getYear());
            }
            yearEditor.setText(String.valueOf(ym.getYear()));

            calendarGrid.getChildren().clear();

            DayOfWeek[] days = DayOfWeek.values();
            for (int i = 0; i < days.length; i++) {
                Label dayHeader = new Label(days[i].getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
                dayHeader.getStyleClass().add("quick-date-day-header");
                dayHeader.setAlignment(Pos.CENTER);
                dayHeader.setMinSize(32, 22);
                dayHeader.setPrefSize(32, 22);
                calendarGrid.add(dayHeader, i, 0);
            }

            LocalDate first = ym.atDay(1);
            int firstColumn = first.getDayOfWeek().getValue() - 1; // Monday = 0
            int daysInMonth = ym.lengthOfMonth();
            LocalDate selected = getValue();
            LocalDate today = LocalDate.now();

            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = ym.atDay(day);
                int index = firstColumn + day - 1;
                int row = 1 + index / 7;
                int column = index % 7;

                Button dayButton = new Button(String.valueOf(day));
                dayButton.getStyleClass().add("quick-date-day");
                dayButton.setMinSize(32, 28);
                dayButton.setPrefSize(32, 28);
                dayButton.setMaxSize(32, 28);

                if (date.equals(today)) {
                    dayButton.getStyleClass().add("quick-date-today");
                }
                if (date.equals(selected)) {
                    dayButton.getStyleClass().add("quick-date-selected");
                }

                dayButton.setOnAction(event -> {
                    setValue(date);
                    hide();
                });

                calendarGrid.add(dayButton, column, row);
            }
        };

        monthSelector.setOnAction(event -> {
            Month month = monthSelector.getValue();
            if (month != null) {
                displayedMonth[0] = YearMonth.of(displayedMonth[0].getYear(), month);
                refreshCalendar.run();
            }
        });

        Runnable commitYear = () -> {
            String text = yearEditor.getText() == null ? "" : yearEditor.getText().trim();
            if (text.isEmpty()) {
                yearEditor.setText(String.valueOf(displayedMonth[0].getYear()));
                return;
            }
            try {
                int year = Integer.parseInt(text);
                year = Math.max(MIN_YEAR, Math.min(MAX_YEAR, year));
                displayedMonth[0] = YearMonth.of(year, displayedMonth[0].getMonth());
                refreshCalendar.run();
            } catch (NumberFormatException ex) {
                yearEditor.setText(String.valueOf(displayedMonth[0].getYear()));
            }
        };

        yearEditor.setOnAction(event -> commitYear.run());
        yearEditor.focusedProperty().addListener((obs, oldFocused, focused) -> {
            if (!focused) {
                commitYear.run();
            }
        });
        yearSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && newValue != displayedMonth[0].getYear()) {
                displayedMonth[0] = YearMonth.of(newValue, displayedMonth[0].getMonth());
                refreshCalendar.run();
            }
        });

        previousMonth.setOnAction(event -> {
            if (displayedMonth[0].isAfter(YearMonth.of(MIN_YEAR, Month.JANUARY))) {
                displayedMonth[0] = displayedMonth[0].minusMonths(1);
                refreshCalendar.run();
            }
        });
        nextMonth.setOnAction(event -> {
            if (displayedMonth[0].isBefore(YearMonth.of(MAX_YEAR, Month.DECEMBER))) {
                displayedMonth[0] = displayedMonth[0].plusMonths(1);
                refreshCalendar.run();
            }
        });

        refreshCalendar.run();

        VBox wrapper = new VBox(selectors, navigation, calendarGrid);
        wrapper.getStyleClass().add("quick-date-popup");
        wrapper.setMinWidth(300);
        wrapper.setPrefWidth(300);
        wrapper.setMaxWidth(300);
        return wrapper;
    }
}
