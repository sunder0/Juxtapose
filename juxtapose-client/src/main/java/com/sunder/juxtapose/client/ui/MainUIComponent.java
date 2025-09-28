package com.sunder.juxtapose.client.ui;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.sunder.juxtapose.client.ClientOperate;
import com.sunder.juxtapose.client.ProxyCoreComponent;
import com.sunder.juxtapose.client.SystemAppContext;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.conf.ProxyRuleConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig;
import static com.sunder.juxtapose.client.ui.UIUtils.createMinimizeAlert;
import com.sunder.juxtapose.client.ui.def.RateDisplay;
import com.sunder.juxtapose.client.ui.panel.GeneralPanel;
import com.sunder.juxtapose.client.ui.panel.LogsPanel;
import com.sunder.juxtapose.client.ui.panel.ProxiesPanel;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.ConfigManager;
import com.sunder.juxtapose.common.LogModule;
import com.sunder.juxtapose.common.MultiProtocolResource;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author : denglinhai
 * @date : 10:13 2025/09/22
 */
public class MainUIComponent extends BaseComponent<ProxyCoreComponent> {
    public final static String NAME = "MAIN_UI_COMPONENT";

    private final Executor mainUIExecutor;
    private static Map<String, VBox> panels = new HashMap<>();
    private static HBox connectBox;
    private ClientOperate clientOperate;

    public MainUIComponent(ProxyCoreComponent parent, ClientOperate clientOperate) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
        this.mainUIExecutor =
                Executors.newSingleThreadExecutor(ThreadFactoryBuilder.create().setNamePrefix("main-ui-").build());
        this.clientOperate = clientOperate;
    }

    @Override
    protected void initInternal() {
        new JFXPanel(); // 主要是初始化JavaFx组件
        ConfigManager<?> configManager = getConfigManager();

        ClientConfig ccfg = configManager.getConfigByName(ClientConfig.NAME, ClientConfig.class);
        connectBox = new HBox(5);
        addModule(new GeneralPanel(this, ccfg, clientOperate, connectBox));

        ProxyServerConfig pscfg = configManager.getConfigByName(ProxyServerConfig.NAME, ProxyServerConfig.class);
        ProxyRuleConfig prcfg = configManager.getConfigByName(ProxyRuleConfig.NAME, ProxyRuleConfig.class);
        addModule(new ProxiesPanel(this, pscfg, prcfg));

        LogModule<?> logModule = getModuleByName(LogModule.NAME, true, LogModule.class);
        addModule(new LogsPanel(this, ccfg, logModule));
    }

    @Override
    protected void startInternal() {
        mainUIExecutor.execute(() -> MainUI.launch(MainUI.class));
    }

    /**
     * 注册主vbox到主界面，用于切换显示
     */
    public void registerVbox(String name, VBox vBox) {
        panels.put(name, vBox);
    }

    /**
     * 主ui应用，需要单独线程启动
     */
    public static class MainUI extends Application {
        StackPane contentArea;
        RateDisplay rateDisplay;
        Timeline timeline;

        @Override
        public void start(Stage primaryStage) {
            // 主布局 - 使用BorderPane
            BorderPane root = new BorderPane();
            root.setStyle("-fx-background-color: #f5f7fa;");

            // 顶部状态栏
            HBox statusBar = createStatusBar();
            root.setTop(statusBar);

            // 左侧导航菜单
            VBox navigation = createNavigation();
            root.setLeft(navigation);

            // 中心内容区域
            contentArea = new StackPane();
            contentArea.setStyle("-fx-background-color: #f5f7fa;");
            contentArea.setPadding(new Insets(15));

            // 初始显示General面板
            showPanel("General");

            root.setCenter(contentArea);

            // 创建场景
            Scene scene = new Scene(root, 750, 550);
            primaryStage.setScene(scene);

            // 添加图标
            MultiProtocolResource resource = new MultiProtocolResource("conf/icon/Juxtapose_icon.jpg", true);
            Image icon = new Image(resource.getResource().getStream());
            primaryStage.getIcons().add(icon);

            MinimizedSystemTray systemTray = new MinimizedSystemTray(primaryStage);
            systemTray.setupSystemTray();

            primaryStage.setOnCloseRequest(event -> {
                // 阻止默认关闭行为
                event.consume();

                Alert alert = createMinimizeAlert("Confirm closing the client?", null);
                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        boolean close = ((CheckBox) ((VBox) alert.getDialogPane().getContent()).getChildren()
                                .get(0)).isSelected();
                        systemTray.minimizeToTray(!close);
                    }
                });

            });

            primaryStage.show();

            // 启动速率更新定时器
            startRateUpdater();
        }

        @Override
        public void stop() {
            // 停止定时器
            if (timeline != null) {
                timeline.stop();
            }
        }

        // 启动速率更新定时器
        private void startRateUpdater() {
            timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                updateRates();
            }));
            timeline.setCycleCount(Animation.INDEFINITE);
            timeline.play();
        }

        // 更新上传下载速率（模拟数据）
        private void updateRates() {
            double uploadRate = (double) SystemAppContext.CONTEXT.getUploadBytes() / 1024;
            double downloadRate = (double) SystemAppContext.CONTEXT.getDownloadBytes() / 1024;

            if (rateDisplay != null) {
                rateDisplay.updateRates(uploadRate, downloadRate);
            }
        }

        // 显示指定面板
        private void showPanel(String panelName) {
            contentArea.getChildren().clear();
            contentArea.getChildren().add(panels.get(panelName));
        }

        // 创建顶部状态栏
        private HBox createStatusBar() {
            HBox statusBar = new HBox();
            statusBar.setPadding(new Insets(8, 15, 8, 15));
            statusBar.setStyle("-fx-background-color: white; -fx-border-color: #e1e4e8; -fx-border-width: 0 0 1 0;");
            statusBar.setSpacing(15);
            statusBar.setAlignment(Pos.CENTER_LEFT);

            // 使用独立的速率显示组件
            rateDisplay = new RateDisplay();

            // 标题
            Label title = new Label("JUXTAPOSE");
            title.setFont(Font.font("Segoe UI", 19));
            title.setTextFill(Color.rgb(33, 150, 243));

            // 填充空间
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 系统状态
            HBox systemStatus = new HBox(5);
            systemStatus.setAlignment(Pos.CENTER);

            Circle statusIndicator = new Circle(5, Color.rgb(76, 175, 80));
            Label statusLabel = new Label("Running");
            statusLabel.setTextFill(Color.rgb(76, 175, 80));
            statusLabel.setFont(Font.font("Segoe UI", 12));

            systemStatus.getChildren().addAll(statusIndicator, statusLabel);

            statusBar.getChildren().addAll(title, spacer, rateDisplay);
            return statusBar;
        }

        // 创建左侧导航菜单
        private VBox createNavigation() {
            VBox navigation = new VBox();
            navigation.setPrefWidth(150);
            navigation.setStyle("-fx-background-color: white; -fx-border-color: #e1e4e8; -fx-border-width: 0 1 0 0;");
            navigation.setPadding(new Insets(15, 0, 0, 0));
            navigation.setSpacing(3);

            // 导航按钮
            ToggleButton generalBtn = createNavButton("General");
            generalBtn.setSelected(true);

            ToggleButton proxiesBtn = createNavButton("Proxies");
            // ToggleButton profilesBtn = createNavButton("Profiles");
            // ToggleButton settingsBtn = createNavButton("Settings");
            ToggleButton logsBtn = createNavButton("Logs");

            // 按钮组
            ToggleGroup navGroup = new ToggleGroup();
            generalBtn.setToggleGroup(navGroup);
            proxiesBtn.setToggleGroup(navGroup);
            // profilesBtn.setToggleGroup(navGroup);
            // settingsBtn.setToggleGroup(navGroup);
            logsBtn.setToggleGroup(navGroup);

            // 添加事件处理
            generalBtn.setOnAction(e -> showPanel("General"));
            proxiesBtn.setOnAction(e -> showPanel("Proxies"));
            // profilesBtn.setOnAction(e -> showPanel("Profiles"));
            // settingsBtn.setOnAction(e -> showPanel("Settings"));
            logsBtn.setOnAction(e -> showPanel("Logs"));

            // 添加按钮到导航栏
            // navigation.getChildren().addAll(generalBtn, proxiesBtn, profilesBtn, settingsBtn, logsBtn);
            navigation.getChildren().addAll(generalBtn, proxiesBtn, logsBtn);

            // 底部区域
            Region spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);

            // 连接按钮 - 优化为居中显示
            connectBox.setPadding(new Insets(10));
            connectBox.setAlignment(Pos.CENTER); // 居中显示
            connectBox.setStyle("-fx-background-color: #e3f2fd;");

            Circle statusIndicator = new Circle(5, Color.rgb(150, 150, 150));
            Label connectLabel = new Label("PENDING");
            connectLabel.setTextFill(Color.rgb(33, 150, 243));
            connectLabel.setFont(Font.font("Segoe UI", 12));
            connectLabel.setStyle("-fx-font-weight: 500;");
            connectBox.getChildren().addAll(statusIndicator, connectLabel);

            navigation.getChildren().addAll(spacer, connectBox);

            return navigation;
        }

        // 创建导航按钮 - 字体大、粗且居中显示
        private ToggleButton createNavButton(String text) {
            ToggleButton button = new ToggleButton(text);
            button.setPrefWidth(150);
            button.setAlignment(Pos.CENTER); // 居中显示
            button.setPadding(new Insets(10, 10, 10, 10));
            button.setStyle(
                    "-fx-background-color: transparent; " +
                            "-fx-text-fill: #495057; " +
                            "-fx-border-width: 0; " +
                            "-fx-font-family: 'Segoe UI'; " +
                            "-fx-font-size: 14px; " + // 字体大
                            "-fx-font-weight: bold;" // 字体粗
            );

            // 选中样式
            button.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    button.setStyle(
                            "-fx-background-color: #e3f2fd; " +
                                    "-fx-text-fill: #2196f3; " +
                                    "-fx-border-width: 0 0 0 3; " +
                                    "-fx-border-color: #2196f3; " +
                                    "-fx-font-family: 'Segoe UI'; " +
                                    "-fx-font-size: 14px; " +
                                    "-fx-font-weight: bold;"
                    );
                } else {
                    button.setStyle(
                            "-fx-background-color: transparent; " +
                                    "-fx-text-fill: #495057; " +
                                    "-fx-border-width: 0; " +
                                    "-fx-font-family: 'Segoe UI'; " +
                                    "-fx-font-size: 14px; " +
                                    "-fx-font-weight: bold;"
                    );
                }
            });

            // 悬停效果
            button.setOnMouseEntered(e -> {
                if (!button.isSelected()) {
                    button.setStyle(
                            "-fx-background-color: #f1f8e9; " +
                                    "-fx-text-fill: #495057; " +
                                    "-fx-border-width: 0; " +
                                    "-fx-font-family: 'Segoe UI'; " +
                                    "-fx-font-size: 14px; " +
                                    "-fx-font-weight: bold;"
                    );
                }
            });

            button.setOnMouseExited(e -> {
                if (!button.isSelected()) {
                    button.setStyle(
                            "-fx-background-color: transparent; " +
                                    "-fx-text-fill: #495057; " +
                                    "-fx-border-width: 0; " +
                                    "-fx-font-family: 'Segoe UI'; " +
                                    "-fx-font-size: 14px; " +
                                    "-fx-font-weight: bold;"
                    );
                }
            });

            return button;
        }
    }

}
