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

/** Selector for one of the two independent company masters. */
public final class CompanySelector extends VBox {
    private final CompanyService.CompanyType companyType;
    private final ComboBox<String> savedCompany = new ComboBox<>();
    private final TextField editor = new TextField();
    private final Button newButton = new Button("+ New Company");
    private final Popup suggestionPopup = new Popup();
    private final ListView<String> suggestionList = new ListView<>();
    private final Consumer<CompanyService.CompanyRecord> selectionListener;
    private final Runnable companyMasterChangedListener;
    private List<CompanyService.CompanyRecord> allCompanies = List.of();
    private CompanyService.CompanyRecord selectedCompany;
    private boolean internalChange;

    public CompanySelector(CompanyService.CompanyType companyType, Consumer<CompanyService.CompanyRecord> selectionListener,
                           Runnable companyMasterChangedListener) {
        this.companyType = companyType;
        this.selectionListener = selectionListener;
        this.companyMasterChangedListener = companyMasterChangedListener == null ? () -> {} : companyMasterChangedListener;
        setSpacing(6); setMaxWidth(Double.MAX_VALUE);
        savedCompany.setPromptText("— Select saved company —"); savedCompany.setMaxWidth(Double.MAX_VALUE); savedCompany.getStyleClass().add("settings-field");
        editor.setPromptText("Company name"); InputLimits.maxLength(editor, InputLimits.COMPANY_NAME); editor.setMaxWidth(Double.MAX_VALUE); editor.getStyleClass().add("settings-field");
        newButton.getStyleClass().add("secondary-button");
        HBox inputRow = new HBox(8, editor, newButton); inputRow.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(editor, Priority.ALWAYS);
        suggestionList.setMaxHeight(180); suggestionList.setPrefWidth(420); suggestionList.setFocusTraversable(false); suggestionList.getStyleClass().add("company-suggestion-list");
        suggestionPopup.setAutoHide(true); suggestionPopup.setHideOnEscape(true); suggestionPopup.getContent().add(suggestionList);
        getChildren().addAll(savedCompany, inputRow);

        editor.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!internalChange) { selectedCompany = null; savedCompany.getSelectionModel().clearSelection(); filterAndShowSuggestions(newValue); notifySelection(); }
        });
        savedCompany.setOnAction(event -> { String name = savedCompany.getSelectionModel().getSelectedItem(); if (name != null && !name.isBlank()) { CompanyService.CompanyRecord r = findByCompanyName(name); if (r != null) selectRecord(r); } });
        editor.setOnAction(event -> { String text = getCompanyName(); CompanyService.CompanyRecord r = findExact(text); if (r == null && !suggestionList.getSelectionModel().isEmpty()) r = findByCompanyName(suggestionList.getSelectionModel().getSelectedItem()); if (r != null) selectRecord(r); else hideSuggestions(); });
        suggestionList.setOnMouseClicked(event -> { if (event.getClickCount() == 1) selectHighlightedSuggestion(); });
        suggestionList.setOnKeyPressed(event -> { switch (event.getCode()) { case ENTER -> { selectHighlightedSuggestion(); event.consume(); } case ESCAPE -> { hideSuggestions(); editor.requestFocus(); event.consume(); } default -> {} } });
        newButton.setOnAction(event -> createNewCompany());
        refreshCompanies();
    }

    public CompanyService.CompanyRecord getSelectedCompany() { return selectedCompany; }
    public Long getSelectedCompanyId() { return selectedCompany == null ? null : selectedCompany.id(); }
    public String getCompanyName() { return editor.getText() == null ? "" : editor.getText().trim(); }
    public javafx.beans.property.StringProperty companyNameProperty() { return editor.textProperty(); }
    public boolean isExistingCompanySelected() { return selectedCompany != null; }

    public void refreshCompanies() {
        String previousName = selectedCompany == null ? null : selectedCompany.companyName();
        allCompanies = CompanyService.findActiveCompanies(companyType);
        savedCompany.getItems().setAll(allCompanies.stream().map(CompanyService.CompanyRecord::companyName).toList());
        if (previousName != null) { CompanyService.CompanyRecord r = findByCompanyName(previousName); if (r != null) selectRecord(r); }
        else filterAndShowSuggestions(editor.getText());
    }

    public void selectCompany(long companyId) { CompanyService.CompanyRecord r = CompanyService.findById(companyType, companyId); if (r == null) clearSelection(); else selectRecord(r); }
    public void selectCompanyByName(String companyName) {
        if (companyName == null || companyName.isBlank()) { clearSelection(); return; }
        CompanyService.CompanyRecord r = CompanyService.findByName(companyType, companyName.trim(), true);
        if (r == null) r = CompanyService.findByName(companyType, companyName.trim(), false);
        if (r == null) setTypedCompanyName(companyName); else selectRecord(r);
    }
    public void clearSelection() { internalChange = true; selectedCompany = null; editor.clear(); savedCompany.getSelectionModel().clearSelection(); internalChange = false; hideSuggestions(); notifySelection(); }
    public void setTypedCompanyName(String companyName) { internalChange = true; selectedCompany = null; savedCompany.getSelectionModel().clearSelection(); editor.setText(companyName == null ? "" : companyName); internalChange = false; filterAndShowSuggestions(editor.getText()); notifySelection(); }
    public void setReadOnly(boolean readOnly) { savedCompany.setDisable(readOnly); editor.setDisable(readOnly); newButton.setDisable(readOnly); if (readOnly) hideSuggestions(); }

    private void selectRecord(CompanyService.CompanyRecord record) { internalChange = true; selectedCompany = record; editor.setText(record.companyName()); if (!savedCompany.getItems().contains(record.companyName())) savedCompany.getItems().add(record.companyName()); savedCompany.getSelectionModel().select(record.companyName()); internalChange = false; hideSuggestions(); notifySelection(); }
    private void filterAndShowSuggestions(String text) { List<CompanyService.CompanyRecord> filtered = filterRecords(text); suggestionList.getItems().setAll(filtered.stream().map(CompanyService.CompanyRecord::companyName).toList()); if (getCompanyName().isBlank() || filtered.isEmpty() || !editor.isFocused()) { hideSuggestions(); return; } suggestionList.getSelectionModel().clearSelection(); showSuggestions(); }
    private void showSuggestions() { if (suggestionList.getItems().isEmpty() || !editor.isFocused() || editor.getScene() == null || editor.getScene().getWindow() == null) return; double width = Math.max(editor.getWidth(), 300); suggestionList.setPrefWidth(width); suggestionList.setMinWidth(width); suggestionList.setMaxWidth(width); var bounds = editor.localToScreen(editor.getBoundsInLocal()); if (bounds != null) suggestionPopup.show(editor, bounds.getMinX(), bounds.getMaxY()); }
    private void hideSuggestions() { if (suggestionPopup.isShowing()) suggestionPopup.hide(); }
    private void selectHighlightedSuggestion() { String name = suggestionList.getSelectionModel().getSelectedItem(); if (name == null || name.isBlank()) return; CompanyService.CompanyRecord r = findByCompanyName(name); if (r != null) selectRecord(r); }
    private List<CompanyService.CompanyRecord> filterRecords(String text) { String term = text == null ? "" : text.trim().toLowerCase(); return allCompanies.stream().filter(c -> term.isBlank() || safe(c.companyName()).toLowerCase().contains(term) || safe(c.contactPerson()).toLowerCase().contains(term) || safe(c.phoneNumber()).toLowerCase().contains(term) || safe(c.emailAddress()).toLowerCase().contains(term)).toList(); }
    private CompanyService.CompanyRecord findExact(String text) { return text == null || text.isBlank() ? null : findByCompanyName(text.trim()); }
    private CompanyService.CompanyRecord findByCompanyName(String companyName) { if (companyName == null || companyName.isBlank()) return null; return allCompanies.stream().filter(c -> c.companyName().equalsIgnoreCase(companyName.trim())).findFirst().orElseGet(() -> CompanyService.findByName(companyType, companyName.trim(), true)); }
    private void notifySelection() { if (selectionListener != null) selectionListener.accept(selectedCompany); }

    private void createNewCompany() {
        Dialog<CompanyService.CompanyRecord> dialog = new Dialog<>(); dialog.setTitle("New " + companyType.displayName() + " Company"); dialog.setHeaderText("Add a company to the " + companyType.displayName() + " master data"); dialog.initModality(Modality.APPLICATION_MODAL);
        TextField name = field("Company name *", InputLimits.COMPANY_NAME); TextField contact = field("Contact person", InputLimits.CONTACT_PERSON);
        TextArea address = new TextArea(); address.setPromptText("Address"); address.setPrefRowCount(3); address.setWrapText(true); address.getStyleClass().add("settings-field");
        TextField phone = field("Phone number", InputLimits.PHONE); TextField email = field("Email address", InputLimits.EMAIL);
        VBox content = new VBox(10, labeled("Company Name", name), labeled("Contact Person", contact), labeled("Address", address), labeled("Phone Number", phone), labeled("Email Address", email)); content.setPrefWidth(480); dialog.getDialogPane().setContent(content);
        ButtonType create = new ButtonType("Create Company", ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);
        dialog.setResultConverter(button -> { if (button != create) return null; try { return CompanyService.create(companyType, name.getText(), contact.getText(), address.getText(), phone.getText(), email.getText()); } catch (RuntimeException ex) { showError(dialog, ex.getMessage()); return null; } });
        dialog.showAndWait().ifPresent(record -> { allCompanies = CompanyService.findActiveCompanies(companyType); savedCompany.getItems().setAll(allCompanies.stream().map(CompanyService.CompanyRecord::companyName).toList()); selectRecord(record); companyMasterChangedListener.run(); });
    }
    private static TextField field(String prompt, int maxLength) { TextField field = new TextField(); field.setPromptText(prompt); InputLimits.maxLength(field, maxLength); field.getStyleClass().add("settings-field"); return field; }
    private static VBox labeled(String text, Control control) { VBox box = new VBox(5, new Label(text), control); control.setMaxWidth(Double.MAX_VALUE); return box; }
    private static String safe(String value) { return value == null ? "" : value; }
    private static void showError(Dialog<?> dialog, String text) { new Alert(Alert.AlertType.ERROR, text == null ? "Unable to create company." : text, ButtonType.OK).showAndWait(); }
}
