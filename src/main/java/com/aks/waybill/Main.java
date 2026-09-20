package com.aks.waybill;

import com.aks.waybill.db.Database;
import com.aks.waybill.ui.DashboardView;
import com.aks.waybill.ui.LoginView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    private Stage stage;
    @Override public void start(Stage primaryStage) {
        stage = primaryStage; Database.initialize(); showLogin();
        stage.setTitle("AKS Waybill"); stage.setMinWidth(1000); stage.setMinHeight(650); stage.show();
    }
    public void showLogin() {
        com.aks.waybill.security.SessionContext.clear();
        var view = new LoginView(this::showDashboard);
        var scene = new Scene(view, 1180, 720);
        scene.getStylesheets().add(getClass().getResource("/com/aks/waybill/css/app.css").toExternalForm());
        stage.setScene(scene); stage.centerOnScreen();
    }
    private void showDashboard(com.aks.waybill.security.AuthService.UserRecord user) {
        var view = new DashboardView(user, this::showLogin);
        var scene = new Scene(view, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/com/aks/waybill/css/app.css").toExternalForm());
        stage.setScene(scene); stage.centerOnScreen();
    }
    public static void main(String[] args) { launch(args); }
}
