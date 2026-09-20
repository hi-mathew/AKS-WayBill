package com.aks.waybill.ui;

import com.aks.waybill.service.CompanyService;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;

import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable company selector backed by the single company master.
 *
 * The company name is intentionally a normal TextField rather than an editable
 * ComboBox.  This prevents JavaFX ComboBox auto-commit behaviour from replacing
 * user-entered text merely because it matches an autocomplete suggestion.
 * Suggestions are informational until the user explicitly selects one.
 */
public final class CompanySelector extends HBox {
    private final TextField editor = new TextField();
    private final Button newButton = new Button("+ New Company");
    private final Popup suggestionPopup = new Popup();
    private final ListView<String> suggestionList = new ListView<>();
    private final Consumer<CompanyService.CompanyRecord> selectionListener;
    private final Runnable companyMasterChangedListener;

    private List<CompanyService.CompanyRecord> allCompanies = List.of();
    private CompanyService.CompanyRecord selectedCompany;
    private boolean internalChange;

    public CompanySelector(Consumer<CompanyService.CompanyRecord> selectionListener) {
        this(selectionListener, () -> {});
    }

    public CompanySelector(Consumer<CompanyService.CompanyRecord> selectionListener,
                           Runnable companyMasterChangedListener) {
        this.selectionListener = selectionListener;
        this.companyMasterChangedListener = companyMasterChangedListener == null
                ? () -> {}
                : companyMasterChangedListener;

        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(Double.MAX_VALUE);

        editor.setPromptText("Company name");
        InputLimits.maxLength(editor, InputLimits.COMPANY_NAME);
        editor.setMaxWidth(Double.MAX_VALUE);
        editor.getStyleClass().add("settings-field");

        suggestionList.setMaxHeight(180);
        suggestionList.setPrefWidth(420);
        suggestionList.setFocusTraversable(false);
        suggestionList.getStyleClass().add("company-suggestion-list");
        suggestionPopup.setAutoHide(true);
        suggestionPopup.setHideOnEscape(true);
        suggestionPopup.getContent().add(suggestionList);

        newButton.getStyleClass().add("secondary-button");
        HBox.setHgrow(editor, Priority.ALWAYS);
        getChildren().addAll(editor, newButton);

        editor.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!internalChange) {
                selectedCompany = null;
                filterAndShowSuggestions(newValue);
                notifySelection();
            }
        });

        editor.setOnAction(event -> {
            // Enter is an explicit user action. Select an exact company name,
            // or the currently highlighted suggestion, but never auto-commit
            // a suggestion merely because the typed text happens to match.
            String text = getCompanyName();
            CompanyService.CompanyRecord record = findExact(text);
            if (record == null && !suggestionList.getSelectionModel().isEmpty()) {
                String selectedName = suggestionList.getSelectionModel().getSelectedItem();
                record = findByCompanyName(selectedName);
            }
            if (record != null) {
                selectRecord(record);
            } else {
                hideSuggestions();
            }
        });

        suggestionList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) {
                selectHighlightedSuggestion();
            }
        });

        suggestionList.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> {
                    selectHighlightedSuggestion();
                    event.consume();
                }
                case ESCAPE -> {
                    hideSuggestions();
                    editor.requestFocus();
                    event.consume();
                }
                default -> { }
            }
        });

        newButton.setOnAction(event -> createNewCompany());

        refreshCompanies();
    }

    public void refreshCompanies() {
        CompanyService.CompanyRecord previous = selectedCompany;
        allCompanies = CompanyService.findActiveCompanies();
        if (previous != null) {
            selectCompany(previous.id());
        } else {
            filterAndShowSuggestions(editor.getText());
        }
    }

    public CompanyService.CompanyRecord getSelectedCompany() {
        return selectedCompany;
    }

    public Long getSelectedCompanyId() {
        return selectedCompany == null ? null : selectedCompany.id();
    }

    /** Returns the current text, including a manually entered new company name. */
    public String getCompanyName() {
        return editor.getText() == null ? "" : editor.getText().trim();
    }

    public boolean isExistingCompanySelected() {
        return selectedCompany != null;
    }

    public void selectCompany(long companyId) {
        CompanyService.CompanyRecord record = CompanyService.findById(companyId);
        if (record == null) {
            clearSelection();
            return;
        }
        selectRecord(record);
    }

    public void selectCompanyByName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            clearSelection();
            return;
        }
        CompanyService.CompanyRecord record = CompanyService.findByName(companyName.trim(), true);
        if (record == null) {
            record = CompanyService.findByName(companyName.trim(), false);
        }
        if (record == null) {
            setTypedCompanyName(companyName);
            return;
        }
        selectRecord(record);
    }

    public void clearSelection() {
        internalChange = true;
        selectedCompany = null;
        editor.clear();
        internalChange = false;
        hideSuggestions();
        notifySelection();
    }

    /** Clears the selection but keeps the supplied company name as a new/manual entry. */
    public void setTypedCompanyName(String companyName) {
        internalChange = true;
        selectedCompany = null;
        editor.setText(companyName == null ? "" : companyName);
        internalChange = false;
        filterAndShowSuggestions(editor.getText());
        notifySelection();
    }

    public void setReadOnly(boolean readOnly) {
        editor.setDisable(readOnly);
        newButton.setDisable(readOnly);
        if (readOnly) hideSuggestions();
    }

    private void selectRecord(CompanyService.CompanyRecord record) {
        internalChange = true;
        selectedCompany = record;
        editor.setText(record.companyName());
        internalChange = false;
        hideSuggestions();
        notifySelection();
    }

    private void filterAndShowSuggestions(String text) {
        List<CompanyService.CompanyRecord> filtered = filterRecords(text);
        suggestionList.getItems().setAll(filtered.stream()
                .map(CompanyService.CompanyRecord::companyName)
                .toList());

        if (getCompanyName().isBlank() || filtered.isEmpty() || !editor.isFocused()) {
            hideSuggestions();
            return;
        }

        suggestionList.getSelectionModel().clearSelection();
        showSuggestions();
    }

    private void showSuggestions() {
        if (suggestionList.getItems().isEmpty() || !editor.isFocused()) return;
        if (editor.getScene() == null || editor.getScene().getWindow() == null) return;

        double width = Math.max(editor.getWidth(), 300);
        suggestionList.setPrefWidth(width);
        suggestionList.setMinWidth(width);
        suggestionList.setMaxWidth(width);

        var bounds = editor.localToScreen(editor.getBoundsInLocal());
        if (bounds == null) return;
        suggestionPopup.show(editor, bounds.getMinX(), bounds.getMaxY());
    }

    private void hideSuggestions() {
        if (suggestionPopup.isShowing()) suggestionPopup.hide();
    }

    private void selectHighlightedSuggestion() {
        String selectedName = suggestionList.getSelectionModel().getSelectedItem();
        if (selectedName == null || selectedName.isBlank()) return;
        CompanyService.CompanyRecord record = findByCompanyName(selectedName);
        if (record != null) selectRecord(record);
    }

    private List<CompanyService.CompanyRecord> filterRecords(String text) {
        String term = text == null ? "" : text.trim().toLowerCase();
        return allCompanies.stream()
                .filter(c -> term.isBlank()
                        || safe(c.companyName()).toLowerCase().contains(term)
                        || safe(c.contactPerson()).toLowerCase().contains(term)
                        || safe(c.phoneNumber()).toLowerCase().contains(term)
                        || safe(c.emailAddress()).toLowerCase().contains(term))
                .toList();
    }

    private CompanyService.CompanyRecord findExact(String text) {
        if (text == null || text.isBlank()) return null;
        return findByCompanyName(text.trim());
    }

    private CompanyService.CompanyRecord findByCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) return null;
        return allCompanies.stream()
                .filter(c -> c.companyName().equalsIgnoreCase(companyName.trim()))
                .findFirst()
                .orElseGet(() -> CompanyService.findByName(companyName.trim(), true));
    }

    private void notifySelection() {
        if (selectionListener != null) selectionListener.accept(selectedCompany);
    }

    private void createNewCompany() {
        Dialog<CompanyService.CompanyRecord> dialog = new Dialog<>();
        dialog.setTitle("New Company");
        dialog.setHeaderText("Add a company to the master data");
        dialog.initModality(Modality.APPLICATION_MODAL);

        TextField name = field("Company name *", InputLimits.COMPANY_NAME);
        TextField contact = field("Contact person", InputLimits.CONTACT_PERSON);
        TextArea address = new TextArea();
        // Address is a text area and intentionally has no character restriction.
        address.setPromptText("Address");
        address.setPrefRowCount(3);
        address.setWrapText(true);
        address.getStyleClass().add("settings-field");
        TextField phone = field("Phone number", InputLimits.PHONE);
        TextField email = field("Email address", InputLimits.EMAIL);

        VBox content = new VBox(10,
                labeled("Company Name", name),
                labeled("Contact Person", contact),
                labeled("Address", address),
                labeled("Phone Number", phone),
                labeled("Email Address", email));
        content.setPrefWidth(480);
        dialog.getDialogPane().setContent(content);

        ButtonType create = new ButtonType("Create Company", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (button != create) return null;
            try {
                return CompanyService.create(name.getText(), contact.getText(), address.getText(), phone.getText(), email.getText());
            } catch (RuntimeException ex) {
                showError(dialog, ex.getMessage());
                return null;
            }
        });

        dialog.showAndWait().ifPresent(record -> {
            allCompanies = CompanyService.findActiveCompanies();
            selectRecord(record);
            // Notify the owning form so the other selector refreshes immediately.
            companyMasterChangedListener.run();
        });
    }

    private static TextField field(String prompt, int maxLength) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        InputLimits.maxLength(field, maxLength);
        field.getStyleClass().add("settings-field");
        return field;
    }

    private static VBox labeled(String text, Control control) {
        VBox box = new VBox(5, new Label(text), control);
        control.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void showError(Dialog<?> dialog, String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR,
                text == null ? "Unable to create company." : text,
                ButtonType.OK);
        alert.initOwner(dialog.getDialogPane().getScene().getWindow());
        alert.showAndWait();
    }
}
