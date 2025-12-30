package com.sunder.juxtapose.client.ui.panel;

import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.ui.MainUIComponent;
import com.sunder.juxtapose.common.BaseModule;
import com.sunder.juxtapose.common.LogModule;
import com.sunder.juxtapose.common.utils.LogFileTailer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sunder.juxtapose.client.ui.UIUtils.createPanelContainer;
import static com.sunder.juxtapose.client.ui.UIUtils.styleButton;

/**
 * @author : sunder
 * @date : 15:41 2025/09/22
 */
public class LogsPanel extends BaseModule<MainUIComponent> {

    private final Logger logger;
    private VBox mainPane;
    private ClientConfig ccfg;
    private LogModule<?> logModule;
    // 定义最大日志行数
    private static final int MAX_LOG_LINES = 2000;
    // 定义ObservableList作为ListView的数据源（存储日志行）
    private ObservableList<String> logItems = FXCollections.observableArrayList();

    public LogsPanel(MainUIComponent belongComponent, ClientConfig ccfg, LogModule<?> logModule) {
        super("LOGS_PANEL", belongComponent);
        this.ccfg = ccfg;
        this.logModule = logModule;
        this.logger = LoggerFactory.getLogger(LogsPanel.class);
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
        ListView<String> logListView = new ListView<>(logItems);
        logListView.setPrefHeight(350);
        logListView.setStyle(
                "-fx-control-inner-background: white; " +
                        "-fx-text-fill: #495057; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-radius: 4; " +
                        "-fx-font-family: 'Consolas'; " +
                        "-fx-font-size: 11px;"
        );

        String logPath = logModule.getCurrentLogPath();
        LogFileTailer logTailer = new LogFileTailer(logPath);
        try {
            logTailer.start(line -> Platform.runLater(() -> {
                // 达到上限移除旧日志
                if (logItems.size() >= MAX_LOG_LINES) {
                    logItems.remove(0);
                }

                logItems.add(line);
                logListView.scrollTo(logItems.size() - 1);
            }));
        } catch (Exception ex) {
            logger.error("Log appending configuration failed", ex);
        }

        clearLogsBtn.setOnMouseClicked(e -> {
            logItems.clear();
        });

        mainPane.getChildren().addAll(logLevelBox, logListView);
        belongComponent.registerVbox("Logs", mainPane);
    }

}
