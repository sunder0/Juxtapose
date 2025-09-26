package com.sunder.juxtapose.client.ui.panel;

import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.ui.MainUIComponent;
import static com.sunder.juxtapose.client.ui.UIUtils.createPanelContainer;
import static com.sunder.juxtapose.client.ui.UIUtils.styleButton;
import com.sunder.juxtapose.common.BaseModule;
import com.sunder.juxtapose.common.LogModule;
import com.sunder.juxtapose.common.utils.LogFileTailer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * @author : denglinhai
 * @date : 15:41 2025/09/22
 */
public class LogsPanel extends BaseModule<MainUIComponent> {

    private VBox mainPane;
    private ClientConfig ccfg;
    private LogModule<?> logModule;

    public LogsPanel(MainUIComponent belongComponent, ClientConfig ccfg, LogModule<?> logModule) {
        super("LOGS_PANEL", belongComponent);
        this.ccfg = ccfg;
        this.logModule = logModule;
        initialize();
    }

    public void initialize() {
        mainPane = createPanelContainer("Connection Logs");

        // 日志级别选择
        HBox logLevelBox = new HBox(8);
        logLevelBox.setAlignment(Pos.CENTER_LEFT);
        logLevelBox.setPadding(new Insets(0, 0, 8, 0));

        Button clearLogsBtn = new Button("Clear Logs");
        styleButton(clearLogsBtn, "#6c757d");
        clearLogsBtn.setPrefSize(80, 28);

        logLevelBox.getChildren().addAll(clearLogsBtn);

        // 日志区域
        TextArea logArea = new TextArea();
        logArea.setStyle(
                "-fx-control-inner-background: white; " +
                        "-fx-text-fill: #495057; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-radius: 4; " +
                        "-fx-font-family: 'Consolas'; " +
                        "-fx-font-size: 11px;"
        );
        logArea.setPrefHeight(350);
        logArea.setEditable(false);

        String logPath = logModule.getCurrentLogPath();
        try (BufferedReader br = new BufferedReader(new FileReader(logPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                logArea.appendText(line + "\n");
            }

            LogFileTailer logTailer = new LogFileTailer(logPath);
            logTailer.start(line1 -> Platform.runLater(() -> logArea.appendText(line1 + "\n")));
        } catch (Exception ignore) {
        }

        clearLogsBtn.setOnMouseClicked(e -> {
            logArea.setText("");
        });

        mainPane.getChildren().addAll(logLevelBox, logArea);
        belongComponent.registerVbox("Logs", mainPane);
    }

}
