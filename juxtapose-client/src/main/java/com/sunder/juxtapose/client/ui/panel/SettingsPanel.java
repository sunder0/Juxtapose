package com.sunder.juxtapose.client.ui.panel;

import com.sunder.juxtapose.client.ClientOperate;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.ui.MainUIComponent;
import static com.sunder.juxtapose.client.ui.UIUtils.createPanelContainer;
import static com.sunder.juxtapose.client.ui.UIUtils.createSettingSection;
import static com.sunder.juxtapose.client.ui.UIUtils.createToggleSetting;
import static com.sunder.juxtapose.client.ui.UIUtils.styleComboBox;
import com.sunder.juxtapose.common.BaseModule;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * @author : denglinhai
 * @date : 15:39 2025/09/22
 */
public class SettingsPanel extends BaseModule<MainUIComponent> {

    private VBox mainPane;
    private ClientConfig ccfg;
    private ClientOperate clientOperate;

    public SettingsPanel(MainUIComponent belongComponent, ClientConfig ccfg, ClientOperate clientOperate) {
        super("SETTINGS_PANEL", belongComponent);
        this.ccfg = ccfg;
        this.clientOperate = clientOperate;
        initialize();
    }

    public void initialize() {
        mainPane = createPanelContainer("Application Settings");

        VBox appSettings = createSettingSection("Application");
        appSettings.getChildren().addAll(
                createToggleSetting("Start with Windows", true, new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) {

                    }
                }),
                createToggleSetting("Minimize to tray", true, new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) {

                    }
                }),
                createToggleSetting("Auto-check updates", true, new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) {

                    }
                })
        );

        VBox themeSettings = createSettingSection("Theme");
        ComboBox<String> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll("Light", "Dark", "System");
        themeCombo.setValue("Light");
        styleComboBox(themeCombo);
        themeSettings.getChildren().add(themeCombo);

        mainPane.getChildren().addAll(appSettings, themeSettings);
        belongComponent.registerVbox("Settings", mainPane);
    }

}
