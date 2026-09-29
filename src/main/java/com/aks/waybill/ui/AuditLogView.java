package com.aks.waybill.ui;

import com.aks.waybill.service.AuditLogService;
import com.aks.waybill.service.SettingsService;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.format.DateTimeFormatter;

public final class AuditLogView extends AppView {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a");
    private final TextField search=new TextField();
    private final TableView<AuditLogService.AuditRecord> table=new TableView<>();
    private final PaginationControl pagination=new PaginationControl();
    private int currentPage=0; private int pageSize=20; private int totalPages=1; private String sortKey="createdAt"; private boolean sortAscending=false;
    public AuditLogView(){super("Audit Log","Review important application activity such as waybill creation, updates, backups and restores.");pageSize=SettingsService.getPageSize();VBox content=build();getChildren().add(content);VBox.setVgrow(content,Priority.ALWAYS);load(0);}
    private VBox build(){
        VBox root=new VBox(14);root.setPadding(new Insets(4,0,30,0));
        HBox bar=new HBox(10);bar.setAlignment(Pos.CENTER_LEFT);search.setPromptText("Search user, action, entity or details");InputLimits.maxLength(search,400);HBox.setHgrow(search,Priority.ALWAYS);Button find=new Button("Search");find.getStyleClass().add("primary-button");find.setOnAction(e->load(0));Button clear=new Button("Clear");clear.getStyleClass().add("secondary-button");clear.setOnAction(e->{search.clear();load(0);});Button refresh=new Button("Refresh");refresh.getStyleClass().add("secondary-button");refresh.setOnAction(e->load(currentPage));bar.getChildren().addAll(search,find,clear,refresh);
        table.setFixedCellSize(30);
        TableColumn<AuditLogService.AuditRecord,String> date=col("Date / Time",r->DATE.format(r.createdAt()),160);TableColumn<AuditLogService.AuditRecord,String> user=col("User",AuditLogService.AuditRecord::userName,120);TableColumn<AuditLogService.AuditRecord,String> action=col("Action",AuditLogService.AuditRecord::action,100);TableColumn<AuditLogService.AuditRecord,String> entity=col("Entity",r->{ if ("WAYBILL".equalsIgnoreCase(r.entityType()) && r.entityLabel()!=null && !r.entityLabel().isBlank()) return r.entityLabel(); return r.entityType()+(r.entityId()==null?"":" #"+r.entityId()); },210);TableColumn<AuditLogService.AuditRecord,String> details=col("Details",AuditLogService.AuditRecord::details,500);table.getColumns().setAll(date,user,action,entity,details);
        table.setOnSort(event->{if(table.getSortOrder().isEmpty())return;TableColumn<?,?> selected=table.getSortOrder().get(0);sortKey=selected==date?"createdAt":selected==user?"user":selected==action?"action":selected==entity?"entity":"details";sortAscending=selected.getSortType()==TableColumn.SortType.ASCENDING;event.consume();load(0);});
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);table.setPlaceholder(new Label("No audit entries found."));table.setPrefHeight(360);table.setMinHeight(72);table.setMaxHeight(360);VBox.setVgrow(table,Priority.NEVER);
        pagination.setPageLoader(this::load);
        root.getChildren().addAll(bar,table,pagination);return root;
    }
    private void load(int requested){try{AuditLogService.Page p=AuditLogService.findPage(search.getText(),Math.max(0,requested),pageSize,sortKey,sortAscending);currentPage=p.page();totalPages=p.totalPages();table.getItems().setAll(p.rows());updateTableHeight(p.rows().size());pagination.setPageData(currentPage,totalPages,p.totalRows(),pageSize);}catch(Exception e){new Alert(Alert.AlertType.ERROR,e.getMessage(),ButtonType.OK).showAndWait();}}

    private void updateTableHeight(int rowCount){
        // Keep the table compact and show only the rows that actually exist on the current page.
        // This removes the empty placeholder rows seen on short/final pages while retaining
        // enough space for the table header and a single placeholder row when there is no data.
        int visibleRows=Math.max(1,rowCount);
        double headerHeight=36;
        double height=headerHeight+(visibleRows*30)+4;
        table.setMinHeight(Math.max(72, Math.min(height, 70)));
        table.setPrefHeight(height);
        table.setMaxHeight(height);
        VBox.setVgrow(table,Priority.NEVER);
    }
    private static TableColumn<AuditLogService.AuditRecord,String> col(String title,java.util.function.Function<AuditLogService.AuditRecord,String> fn,double width){TableColumn<AuditLogService.AuditRecord,String> c=new TableColumn<>(title);c.setPrefWidth(width);c.setCellValueFactory(d->new SimpleStringProperty(fn.apply(d.getValue())));return c;}
}
