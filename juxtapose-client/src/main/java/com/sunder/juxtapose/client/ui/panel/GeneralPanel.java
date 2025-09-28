package com.sunder.juxtapose.client.ui.panel;

import com.sunder.juxtapose.client.ClientOperate;
import com.sunder.juxtapose.client.SystemAppContext;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.ui.MainUIComponent;
import static com.sunder.juxtapose.client.ui.UIUtils.createEditableValueRow;
import static com.sunder.juxtapose.client.ui.UIUtils.createPanelContainer;
import static com.sunder.juxtapose.client.ui.UIUtils.createSettingSection;
import static com.sunder.juxtapose.client.ui.UIUtils.createToggleSetting;
import static com.sunder.juxtapose.client.ui.UIUtils.styleComboBox;
import com.sunder.juxtapose.common.BaseModule;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;

/**
 * @author : denglinhai
 * @date : 10:31 2025/09/22
 */
public class GeneralPanel extends BaseModule<MainUIComponent> {

    private final ClientConfig ccfg;
    private ClientOperate clientOperate;
    private VBox mainPane;
    private HBox connectBox;

    public GeneralPanel(MainUIComponent belongComponent, ClientConfig ccfg, ClientOperate clientOperate,
            HBox connectBox) {
        super("GENERAL_PANEL", belongComponent);
        this.ccfg = ccfg;
        this.clientOperate = clientOperate;
        this.connectBox = connectBox;
        initialize();
    }

    public void initialize() {
        mainPane = createPanelContainer("General Settings");

        // 端口设置
        VBox portSettings = createSettingSection("Port Settings");
        HBox socksPort = createEditableValueRow("Socks Port:", ccfg.getSocks5Port() + "", ccfg::setSocks5Port);
        HBox httpPort = createEditableValueRow("HTTP Port:", ccfg.getHttpPort() + "", ccfg::setHttpPort);
        portSettings.getChildren().addAll(socksPort, httpPort);

        // 模式设置
        VBox modeSettings = createSettingSection("Mode");
        ComboBox<String> modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll("GLOBAL", "RULE", "DIRECT");
        modeCombo.setValue(ccfg.getProxyMode().name());
        modeCombo.setMaxWidth(200);
        modeCombo.setOnAction(event -> {
            String selected = modeCombo.getSelectionModel().getSelectedItem();
            if (selected != null) {
                ccfg.setProxyMode(selected);
            }
        });
        styleComboBox(modeCombo);
        modeSettings.getChildren().add(modeCombo);

        // 开关设置
        VBox toggleSettings = createSettingSection("Settings");
        toggleSettings.getChildren().addAll(
                createLoggingSetting(ccfg.getLogLevel().toUpperCase()),
                createToggleSetting("System Proxy", ccfg.getProxyEnable(), result -> {
                    ccfg.setProxyEnable(result);
                    if (result) {
                        SystemAppContext.CONTEXT.getSystemProxySetting().enableSystemProxy();

                        Circle statusIndicator = new Circle(5, Color.rgb(76, 175, 80));
                        Label connectLabel = new Label("RUNNING");
                        connectLabel.setTextFill(Color.rgb(33, 150, 243));
                        connectLabel.setFont(Font.font("Segoe UI", 12));
                        connectLabel.setStyle("-fx-font-weight: 500;");

                        connectBox.getChildren().set(0, statusIndicator);
                        connectBox.getChildren().set(1, connectLabel);
                    } else {
                        SystemAppContext.CONTEXT.getSystemProxySetting().disableSystemProxy();

                        Circle statusIndicator = new Circle(5, Color.rgb(150, 150, 150));
                        Label connectLabel = new Label("PENDING");
                        connectLabel.setTextFill(Color.rgb(33, 150, 243));
                        connectLabel.setFont(Font.font("Segoe UI", 12));
                        connectLabel.setStyle("-fx-font-weight: 500;");

                        connectBox.getChildren().set(0, statusIndicator);
                        connectBox.getChildren().set(1, connectLabel);
                    }
                })
        );

        mainPane.getChildren().addAll(portSettings, modeSettings, toggleSettings);
        belongComponent.registerVbox("General", mainPane);
    }

    // 创建日志设置
    private HBox createLoggingSetting(String value) {
        HBox row = new HBox();
        row.setSpacing(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));

        Label nameLabel = new Label("Log Level");
        nameLabel.setTextFill(Color.rgb(33, 37, 41));
        nameLabel.setFont(Font.font("Segoe UI", 12));
        nameLabel.setMinWidth(100);

        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll("info", "warning", "error", "debug");
        combo.setValue(value);
        styleComboBox(combo);

        combo.setOnAction(event -> {
            String selected = combo.getSelectionModel().getSelectedItem();
            if (selected != null) {
                ccfg.setLogLevel(selected.toUpperCase());
            }
        });

        row.getChildren().addAll(nameLabel, combo);
        return row;
    }

}
