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
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private final TextField search=new TextField();
    private final TableView<AuditLogService.AuditRecord> table=new TableView<>();
    private final Label info=new Label(); private final Label pageInfo=new Label();
    private final Button previous=new Button("‹"); private final Button next=new Button("›"); private final HBox pages=new HBox(5);
    private int currentPage=0; private int pageSize=20; private int totalPages=1;
    public AuditLogView(){super("Audit Log","Review important application activity such as waybill creation, updates, backups and restores.");pageSize=SettingsService.getPageSize();getChildren().add(build());load(0);}
    private VBox build(){
        VBox root=new VBox(14);root.setPadding(new Insets(4,0,30,0));
        HBox bar=new HBox(10);bar.setAlignment(Pos.CENTER_LEFT);search.setPromptText("Search user, action, entity or details");InputLimits.maxLength(search,400);HBox.setHgrow(search,Priority.ALWAYS);Button find=new Button("Search");find.getStyleClass().add("primary-button");find.setOnAction(e->load(0));Button clear=new Button("Clear");clear.getStyleClass().add("secondary-button");clear.setOnAction(e->{search.clear();load(0);});Button refresh=new Button("Refresh");refresh.getStyleClass().add("secondary-button");refresh.setOnAction(e->load(currentPage));bar.getChildren().addAll(search,find,clear,refresh);
        TableColumn<AuditLogService.AuditRecord,String> date=col("Date / Time",r->DATE.format(r.createdAt()),160);TableColumn<AuditLogService.AuditRecord,String> user=col("User",AuditLogService.AuditRecord::userName,120);TableColumn<AuditLogService.AuditRecord,String> action=col("Action",AuditLogService.AuditRecord::action,100);TableColumn<AuditLogService.AuditRecord,String> entity=col("Entity",r->{ if ("WAYBILL".equalsIgnoreCase(r.entityType()) && r.entityLabel()!=null && !r.entityLabel().isBlank()) return r.entityLabel(); return r.entityType()+(r.entityId()==null?"":" #"+r.entityId()); },210);TableColumn<AuditLogService.AuditRecord,String> details=col("Details",AuditLogService.AuditRecord::details,500);table.getColumns().setAll(date,user,action,entity,details);table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);table.setPlaceholder(new Label("No audit entries found."));table.setPrefHeight(560);VBox.setVgrow(table,Priority.ALWAYS);
        previous.getStyleClass().add("secondary-button");next.getStyleClass().add("secondary-button");previous.setOnAction(e->load(currentPage-1));next.setOnAction(e->load(currentPage+1));pages.setAlignment(Pos.CENTER);HBox paging=new HBox(12,previous,pages,next);paging.setAlignment(Pos.CENTER);Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);HBox bottom=new HBox(10,info,spacer,pageInfo);bottom.setAlignment(Pos.CENTER_LEFT);root.getChildren().addAll(bar,table,bottom,paging);return root;
    }
    private void load(int requested){try{AuditLogService.Page p=AuditLogService.findPage(search.getText(),Math.max(0,requested),pageSize);currentPage=p.page();totalPages=p.totalPages();table.getItems().setAll(p.rows());long start=p.totalRows()==0?0:(long)currentPage*pageSize+1;long end=Math.min(p.totalRows(),(long)(currentPage+1)*pageSize);info.setText("Showing "+start+"–"+end+" of "+p.totalRows());pageInfo.setText("Page "+(currentPage+1)+" of "+totalPages);previous.setDisable(currentPage<=0);next.setDisable(currentPage>=totalPages-1);buildPages();}catch(Exception e){new Alert(Alert.AlertType.ERROR,e.getMessage(),ButtonType.OK).showAndWait();}}
    private void buildPages(){pages.getChildren().clear();int start=Math.max(0,currentPage-2),end=Math.min(totalPages-1,start+4);start=Math.max(0,end-4);for(int i=start;i<=end;i++){int x=i;Button b=new Button(String.valueOf(i+1));b.getStyleClass().add(i==currentPage?"nav-selected":"secondary-button");b.setOnAction(e->load(x));pages.getChildren().add(b);}}
    private static TableColumn<AuditLogService.AuditRecord,String> col(String title,java.util.function.Function<AuditLogService.AuditRecord,String> fn,double width){TableColumn<AuditLogService.AuditRecord,String> c=new TableColumn<>(title);c.setPrefWidth(width);c.setCellValueFactory(d->new SimpleStringProperty(fn.apply(d.getValue())));return c;}
}
