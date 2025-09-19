package com.sunder.juxtapose.client.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;

/**
 * @author : denglinhai
 * @date : 12:16 2025/09/18
 */
public class MainUI extends Application {

    private Map<String, VBox> panels = new HashMap<>();
    private StackPane contentArea;
    private RateDisplay rateDisplay;
    private Timeline timeline;
    private Random random = new Random();

    // Proxies相关组件
    private ListView<ProxyGroup> groupListView;
    private ObservableList<ProxyGroup> groups;
    private Map<String, ObservableList<ProxyNode>> proxyNodesMap = new HashMap<>();
    private Map<String, String> selectedNodeMap = new HashMap<>(); // 存储每个分组选中的节点
    private Map<String, Boolean> groupExpandedMap = new HashMap<>(); // 存储每个分组的展开状态

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

        // 创建所有面板
        panels.put("General", createGeneralPanel());
        panels.put("Proxies", createProxiesPanel());
        panels.put("Profiles", createProfilesPanel());
        panels.put("Settings", createSettingsPanel());
        panels.put("Logs", createLogsPanel());

        // 初始显示General面板
        showPanel("General");

        root.setCenter(contentArea);

        // 创建场景
        Scene scene = new Scene(root, 750, 550);
        //primaryStage.setTitle("JUXTAPOSE");
        primaryStage.setScene(scene);
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
        // 这里只是模拟数据，实际应用中应该从网络接口获取真实数据
        double uploadRate = Math.random() * 10;
        double downloadRate = Math.random() * 100;

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
        title.setFont(Font.font("Segoe UI", 14));
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

        statusBar.getChildren().addAll(rateDisplay, spacer, systemStatus, title);
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
        ToggleButton profilesBtn = createNavButton("Profiles");
        ToggleButton settingsBtn = createNavButton("Settings");
        ToggleButton logsBtn = createNavButton("Logs");

        // 按钮组
        ToggleGroup navGroup = new ToggleGroup();
        generalBtn.setToggleGroup(navGroup);
        proxiesBtn.setToggleGroup(navGroup);
        profilesBtn.setToggleGroup(navGroup);
        settingsBtn.setToggleGroup(navGroup);
        logsBtn.setToggleGroup(navGroup);

        // 添加事件处理
        generalBtn.setOnAction(e -> showPanel("General"));
        proxiesBtn.setOnAction(e -> showPanel("Proxies"));
        profilesBtn.setOnAction(e -> showPanel("Profiles"));
        settingsBtn.setOnAction(e -> showPanel("Settings"));
        logsBtn.setOnAction(e -> showPanel("Logs"));

        // 添加按钮到导航栏
        navigation.getChildren().addAll(generalBtn, proxiesBtn, profilesBtn, settingsBtn, logsBtn);

        // 底部区域
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // 连接按钮 - 优化为居中显示
        HBox connectBox = new HBox(5);
        connectBox.setPadding(new Insets(10));
        connectBox.setAlignment(Pos.CENTER); // 居中显示
        connectBox.setStyle("-fx-background-color: #e3f2fd;");

        Circle statusIndicator = new Circle(5, Color.rgb(76, 175, 80));
        Label connectLabel = new Label("Connected");
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

    // 创建通用面板容器
    private VBox createPanelContainer(String title) {
        VBox panel = new VBox();
        panel.setSpacing(15);
        panel.setPadding(new Insets(20));
        panel.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-color: #e1e4e8; " +
                        "-fx-border-radius: 6; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 3, 0, 0, 1);"
        );

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Segoe UI", 16));
        titleLabel.setTextFill(Color.rgb(33, 37, 41));
        titleLabel.setStyle("-fx-font-weight: 500;");

        panel.getChildren().add(titleLabel);
        return panel;
    }

    // 创建General面板
    private VBox createGeneralPanel() {
        VBox panel = createPanelContainer("General Settings");

        // 端口设置
        VBox portSettings = createSettingSection("Port Settings");
        portSettings.getChildren().addAll(
                createEditableValueRow("Socks Port:", "7890"),
                createEditableValueRow("HTTP Port:", "7891")
        );

        // 模式设置
        VBox modeSettings = createSettingSection("Mode");
        ComboBox<String> modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll("Global", "Rule", "Direct");
        modeCombo.setValue("Rule");
        modeCombo.setMaxWidth(200);
        styleComboBox(modeCombo);
        modeSettings.getChildren().add(modeCombo);

        // 开关设置
        VBox toggleSettings = createSettingSection("Settings");
        toggleSettings.getChildren().addAll(
                createToggleSetting("Log Level", "info"),
                createToggleSetting("System Proxy", false)
        );

        panel.getChildren().addAll(portSettings, modeSettings, toggleSettings);
        return panel;
    }

    // Proxies面板：采用二级列表形式展示
    private VBox createProxiesPanel() {
        VBox panel = createPanelContainer("Proxy Settings");

        // 顶部工具栏
        HBox toolbar = new HBox(8);
        toolbar.setPadding(new Insets(5, 0, 10, 0));

        // URL输入框
        TextField urlField = new TextField();
        urlField.setPromptText("Download from URL");
        styleTextField(urlField);
        urlField.setPrefWidth(300);

        // Download按钮
        Button downloadBtn = new Button("Download");
        styleButton(downloadBtn, "#2196f3");
        downloadBtn.setPrefSize(80, 28);

        // Import按钮
        Button importBtn = new Button("Import");
        styleButton(importBtn, "#4CAF50");
        importBtn.setPrefSize(70, 28);

        // 添加事件处理
        downloadBtn.setOnAction(e -> downloadFromUrl(urlField.getText()));
        importBtn.setOnAction(e -> importFromFile());

        toolbar.getChildren().addAll(urlField, downloadBtn, importBtn);

        // 代理组列表（包含二级节点）
        VBox proxyGroupsSection = createSettingSection("Proxy Groups");

        groupListView = new ListView<>();
        styleListViewForProxies(groupListView);
        groupListView.setPrefHeight(350);

        // 添加选择监听器
        groupListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (oldVal != null) {
                groupListView.refresh();
            }
        });

        // 添加模拟代理组数据
        groups = FXCollections.observableArrayList(
                new ProxyGroup("Global", "Selector"),
                new ProxyGroup("China", "Selector"),
                new ProxyGroup("US", "Selector"),
                new ProxyGroup("Europe", "Selector"),
                new ProxyGroup("Direct", "Direct"),
                new ProxyGroup("Proxy", "URLTest")
        );
        groupListView.setItems(groups);

        // 初始化组的展开状态
        for (ProxyGroup group : groups) {
            groupExpandedMap.put(group.getName(), false);
        }

        // 为代理组添加点击事件和样式
        groupListView.setCellFactory(param -> new GroupListCell());

        proxyGroupsSection.getChildren().add(groupListView);

        // 代理设置 - 优化Profiles Directory样式
        VBox proxySettings = createSettingSection("Proxy Settings");
        proxySettings.getChildren().addAll(
                createDirectorySettingRow("Profiles Directory:", "Open Folder"),
                createEditableValueRow("Update Interval:", "3600")
        );

        panel.getChildren().addAll(toolbar, proxyGroupsSection, proxySettings);
        return panel;
    }

    // 创建目录选择设置行 - 优化样式与Update Interval保持一致
    private HBox createDirectorySettingRow(String label, String value) {
        HBox row = new HBox();
        row.setSpacing(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));

        Label nameLabel = new Label(label);
        nameLabel.setTextFill(Color.rgb(33, 37, 41));
        nameLabel.setFont(Font.font("Segoe UI", 12));
        nameLabel.setMinWidth(120);

        // 使用与Update Interval相同的样式
        Label valueLabel = new Label(value);
        valueLabel.setTextFill(Color.rgb(33, 150, 243)); // 蓝色
        valueLabel.setFont(Font.font("Segoe UI", 12));
        valueLabel.setStyle(
                "-fx-border-color: #3399ff; " +
                        "-fx-border-width: 0 0 1 0; " +
                        "-fx-border-style: dashed; " +
                        "-fx-cursor: hand;"
        );

        // 保持原有功能
        valueLabel.setOnMouseClicked(e -> openDirectory());

        row.getChildren().addAll(nameLabel, valueLabel);
        return row;
    }

    // 打开目录
    private void openDirectory() {
        try {
            // 这里使用用户主目录作为示例，实际应用中应使用真实的配置文件目录
            File directory = new File(System.getProperty("user.home"));

            if (!directory.exists()) {
                directory.mkdirs(); // 如果目录不存在则创建
            }

            // 打开系统文件浏览器
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory);
            } else {
                showAlert("Error", "Desktop is not supported on this system");
            }
        } catch (IOException ex) {
            showAlert("Error", "Failed to open directory: " + ex.getMessage());
        }
    }

    // 从URL下载代理配置
    private void downloadFromUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            showAlert("Error", "Please enter a valid URL");
            return;
        }

        try {
            // 简单模拟从URL下载并替换代理配置
            URL url = new URL(urlString);
            Scanner scanner = new Scanner(url.openStream());
            StringBuilder content = new StringBuilder();

            // 读取URL内容
            while (scanner.hasNextLine()) {
                content.append(scanner.nextLine());
            }
            scanner.close();

            // 清空现有数据
            clearAllProxyData();

            // 添加新的模拟数据
            groups.addAll(
                    new ProxyGroup("Downloaded Global", "Selector"),
                    new ProxyGroup("Downloaded Asia", "Selector"),
                    new ProxyGroup("Downloaded Europe", "Selector")
            );

            // 为新组初始化状态
            for (ProxyGroup group : groups) {
                groupExpandedMap.put(group.getName(), false);

                // 添加模拟节点
                ObservableList<ProxyNode> nodes = FXCollections.observableArrayList();
                nodes.addAll(
                        new ProxyNode(group.getName() + " Node 1", "SOCKS5", (int)(Math.random() * 200) + 10 + "ms"),
                        new ProxyNode(group.getName() + " Node 2", "SSR", (int)(Math.random() * 200) + 10 + "ms")
                );
                proxyNodesMap.put(group.getName(), nodes);
            }

            showAlert("Success", "Proxy configuration downloaded successfully");
            groupListView.refresh();

        } catch (Exception e) {
            showAlert("Error", "Failed to download proxy configuration: " + e.getMessage());
        }
    }

    // 从本地文件导入代理配置
    private void importFromFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Proxy Configuration");

        // 设置文件过滤器，只显示JSON文件
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(
                "JSON files (*.json)", "*.json");
        fileChooser.getExtensionFilters().add(extFilter);

        // 获取当前舞台
        Stage stage = (Stage) contentArea.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            try {
                // 读取文件内容
                String content = new String(Files.readAllBytes(Paths.get(selectedFile.getPath())));

                // 清空现有数据
                clearAllProxyData();

                // 添加新的模拟数据
                groups.addAll(
                        new ProxyGroup("Imported Group 1", "Selector"),
                        new ProxyGroup("Imported Group 2", "URLTest"),
                        new ProxyGroup("Imported Group 3", "Fallback")
                );

                // 为新组初始化状态
                for (ProxyGroup group : groups) {
                    groupExpandedMap.put(group.getName(), false);

                    // 添加模拟节点
                    ObservableList<ProxyNode> nodes = FXCollections.observableArrayList();
                    nodes.addAll(
                            new ProxyNode(group.getName() + " Server 1", "VMESS", (int)(Math.random() * 200) + 10 + "ms"),
                            new ProxyNode(group.getName() + " Server 2", "HTTPS", (int)(Math.random() * 200) + 10 + "ms"),
                            new ProxyNode(group.getName() + " Server 3", "HTTP", (int)(Math.random() * 200) + 10 + "ms")
                    );
                    proxyNodesMap.put(group.getName(), nodes);
                }

                showAlert("Success", "Proxy configuration imported successfully from " + selectedFile.getName());
                groupListView.refresh();

            } catch (IOException e) {
                showAlert("Error", "Failed to import proxy configuration: " + e.getMessage());
            }
        }
    }

    // 清空所有代理数据
    private void clearAllProxyData() {
        groups.clear();
        proxyNodesMap.clear();
        selectedNodeMap.clear();
        groupExpandedMap.clear();
    }

    // 为Proxies面板单独设置ListView样式（无外框）
    private void styleListViewForProxies(ListView<?> listView) {
        listView.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-border-width: 0; " +
                        "-fx-control-inner-background: transparent; " +
                        "-fx-text-fill: #495057; " +
                        "-fx-padding: 0;"
        );
    }

    // 代理组列表项的自定义单元格 - 名称和类型在同一水平线
    private class GroupListCell extends ListCell<ProxyGroup> {
        private VBox container;
        private HBox groupHeader;
        private Label nameLabel;
        private Label typeLabel;
        private Label selectedNodeLabel;
        private VBox nodesContainer;
        private ProxyGroup currentGroup;

        public GroupListCell() {
            // 初始化容器
            container = new VBox(3);

            // 组标题部分
            groupHeader = new HBox(5);
            groupHeader.setPadding(new Insets(8, 12, 8, 12));
            groupHeader.setAlignment(Pos.CENTER_LEFT);
            groupHeader.setCursor(javafx.scene.Cursor.HAND);

            // 添加轻微的悬停效果
            groupHeader.setOnMouseEntered(e -> {
                if (currentGroup != null && !groupExpandedMap.getOrDefault(currentGroup.getName(), false)) {
                    groupHeader.setStyle(
                            "-fx-background-color: #f8f9fa; " +
                                    "-fx-text-fill: #495057; " +
                                    "-fx-font-family: 'Segoe UI';"
                    );
                }
            });

            groupHeader.setOnMouseExited(e -> {
                if (currentGroup != null && !groupExpandedMap.getOrDefault(currentGroup.getName(), false)) {
                    groupHeader.setStyle(
                            "-fx-background-color: transparent; " +
                                    "-fx-text-fill: #495057; " +
                                    "-fx-font-family: 'Segoe UI';"
                    );
                }
            });

            // 组名和类型在同一行显示
            HBox groupInfo = new HBox(10);
            nameLabel = new Label();
            nameLabel.setStyle("-fx-font-weight: 500; -fx-font-size: 13px;");

            HBox typeBox = new HBox(2);
            typeLabel = new Label();
            typeLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 10px;");

            // 展开/折叠指示器
            Label expandIndicator = new Label("+");
            expandIndicator.setPrefWidth(12);
            expandIndicator.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
            expandIndicator.setAlignment(Pos.CENTER);

            // 添加展开/折叠动画
            Timeline expandTimeline = new Timeline(
                    new KeyFrame(Duration.millis(150),
                            new KeyValue(expandIndicator.rotateProperty(), 0, Interpolator.EASE_BOTH))
            );

            typeBox.getChildren().addAll(expandIndicator, typeLabel);
            groupInfo.getChildren().addAll(nameLabel, typeBox);

            // 选中的节点
            selectedNodeLabel = new Label();
            selectedNodeLabel.setStyle("-fx-text-fill: #2196f3; -fx-font-size: 10px;");
            selectedNodeLabel.setPrefWidth(100);

            // 使用Region填充空间
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            groupHeader.getChildren().addAll(groupInfo, spacer, selectedNodeLabel);

            // 节点容器
            nodesContainer = new VBox(6);
            nodesContainer.setPadding(new Insets(0, 5, 8, 30));
            nodesContainer.setVisible(false);
            nodesContainer.setManaged(false);
            nodesContainer.setOpacity(0);
            nodesContainer.setStyle("-fx-background-color: white;");

            container.getChildren().addAll(groupHeader, nodesContainer);

            // 点击组标题时展开/折叠节点
            groupHeader.setOnMouseClicked(e -> {
                if (currentGroup != null) {
                    boolean expanded = !groupExpandedMap.getOrDefault(currentGroup.getName(), false);
                    groupExpandedMap.put(currentGroup.getName(), expanded);

                    // 刷新当前列表项以更新样式
                    updateItem(currentGroup, false);

                    // 展开/折叠动画
                    Timeline timeline = new Timeline(
                            new KeyFrame(Duration.millis(200),
                                    new KeyValue(nodesContainer.opacityProperty(), expanded ? 1 : 0, Interpolator.EASE_BOTH)
                            )
                    );

                    if (expanded) {
                        nodesContainer.setVisible(true);
                        nodesContainer.setManaged(true);
                        timeline.play();
                    } else {
                        timeline.setOnFinished(event -> {
                            nodesContainer.setVisible(false);
                            nodesContainer.setManaged(false);
                        });
                        timeline.play();
                    }

                    // 旋转指示器动画
                    expandTimeline.stop();
                    expandTimeline.getKeyFrames().clear();
                    expandTimeline.getKeyFrames().add(
                            new KeyFrame(Duration.millis(150),
                                    new KeyValue(expandIndicator.rotateProperty(), expanded ? 45 : 0, Interpolator.EASE_BOTH)
                            )
                    );
                    expandTimeline.play();

                    // 如果是第一次展开，加载节点
                    if (expanded && (proxyNodesMap.get(currentGroup.getName()) == null ||
                            proxyNodesMap.get(currentGroup.getName()).isEmpty())) {
                        loadProxyNodes(currentGroup.getName());
                    }
                }
                e.consume();
            });
        }

        @Override
        protected void updateItem(ProxyGroup item, boolean empty) {
            super.updateItem(item, empty);
            currentGroup = item;

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
            } else {
                nameLabel.setText(item.getName());
                typeLabel.setText("(" + item.getType() + ")");

                // 更新选中的节点显示
                String selectedNode = selectedNodeMap.get(item.getName());
                if (selectedNode != null && !selectedNode.isEmpty()) {
                    selectedNodeLabel.setText("✓ " + selectedNode);
                } else {
                    selectedNodeLabel.setText("");
                }

                // 更新展开/折叠状态
                boolean expanded = groupExpandedMap.getOrDefault(item.getName(), false);
                ((Label) ((HBox) ((HBox) groupHeader.getChildren().get(0)).getChildren().get(1)).getChildren().get(0)).setRotate(expanded ? 45 : 0);
                nodesContainer.setVisible(expanded);
                nodesContainer.setManaged(expanded);
                nodesContainer.setOpacity(expanded ? 1 : 0);

                // 设置样式
                setGraphic(container);
                setStyle("-fx-background-color: transparent;");

                groupHeader.setStyle(
                        "-fx-background-color: transparent; " +
                                "-fx-text-fill: #495057; " +
                                "-fx-font-family: 'Segoe UI';"
                );
                nameLabel.setTextFill(Color.rgb(33, 37, 41));

                // 如果展开状态为true，确保节点已加载
                if (expanded) {
                    loadProxyNodes(item.getName());
                }
            }
        }

        // 加载并显示节点
        private void loadProxyNodes(String groupName) {
            nodesContainer.getChildren().clear();

            ObservableList<ProxyNode> nodes = proxyNodesMap.get(groupName);

            // 如果没有该组的数据，则初始化
            if (nodes == null) {
                nodes = FXCollections.observableArrayList();
                // 添加模拟数据
                switch (groupName) {
                    case "Global":
                        nodes.addAll(
                                new ProxyNode("Global Proxy 1", "SOCKS5", (int)(Math.random() * 200) + 10 + "ms"),
                                new ProxyNode("Global Proxy 2", "SOCKS5", (int)(Math.random() * 200) + 10 + "ms"),
                                new ProxyNode("Global Proxy 3", "SOCKS5", (int)(Math.random() * 200) + 10 + "ms")
                        );
                        break;
                    case "China":
                        nodes.addAll(
                                new ProxyNode("China Proxy 1", "SSR", (int)(Math.random() * 100) + 5 + "ms"),
                                new ProxyNode("China Proxy 2", "SSR", (int)(Math.random() * 100) + 5 + "ms")
                        );
                        break;
                    case "US":
                        nodes.addAll(
                                new ProxyNode("US East Proxy", "HTTP", (int)(Math.random() * 300) + 50 + "ms"),
                                new ProxyNode("US West Proxy", "HTTP", (int)(Math.random() * 300) + 50 + "ms"),
                                new ProxyNode("US Central Proxy", "HTTP", (int)(Math.random() * 300) + 50 + "ms")
                        );
                        break;
                    case "Europe":
                        nodes.addAll(
                                new ProxyNode("UK Proxy", "HTTPS", (int)(Math.random() * 350) + 80 + "ms"),
                                new ProxyNode("Germany Proxy", "HTTPS", (int)(Math.random() * 350) + 80 + "ms"),
                                new ProxyNode("France Proxy", "HTTPS", (int)(Math.random() * 350) + 80 + "ms")
                        );
                        break;
                    case "Direct":
                        nodes.addAll(
                                new ProxyNode("Direct Connection", "DIRECT", (int)(Math.random() * 50) + 5 + "ms")
                        );
                        break;
                    case "Proxy":
                        nodes.addAll(
                                new ProxyNode("Proxy Server 1", "VMESS", (int)(Math.random() * 250) + 30 + "ms"),
                                new ProxyNode("Proxy Server 2", "VMESS", (int)(Math.random() * 250) + 30 + "ms"),
                                new ProxyNode("Proxy Server 3", "VMESS", (int)(Math.random() * 250) + 30 + "ms"),
                                new ProxyNode("Proxy Server 4", "VMESS", (int)(Math.random() * 250) + 30 + "ms")
                        );
                        break;
                    default:
                        // 为下载或导入的新组添加默认节点
                        nodes.addAll(
                                new ProxyNode(groupName + " Node 1", "SOCKS5", (int)(Math.random() * 200) + 10 + "ms"),
                                new ProxyNode(groupName + " Node 2", "SSR", (int)(Math.random() * 200) + 10 + "ms")
                        );
                }
                proxyNodesMap.put(groupName, nodes);
            }

            // 创建节点并每行显示两个
            HBox currentRow = new HBox(8);
            currentRow.setSpacing(8);

            for (int i = 0; i < nodes.size(); i++) {
                ProxyNode node = nodes.get(i);
                ProxyNodeBlock block = new ProxyNodeBlock(node, currentGroup);

                // 设置每个节点占据一半宽度
                block.setPrefWidth(220);
                HBox.setHgrow(block, Priority.SOMETIMES);

                currentRow.getChildren().add(block);

                // 每两个节点换行
                if ((i + 1) % 2 == 0) {
                    nodesContainer.getChildren().add(currentRow);
                    currentRow = new HBox(8);
                    currentRow.setSpacing(8);
                }
            }

            // 添加最后一行（如果有剩余节点）
            if (!currentRow.getChildren().isEmpty()) {
                nodesContainer.getChildren().add(currentRow);
            }
        }
    }

    // 按钮样式设置
    private void styleButton(Button button, String color) {
        button.setStyle(
                "-fx-background-color: " + color + "; " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 4 8; " +
                        "-fx-background-radius: 3; " +
                        "-fx-font-family: 'Segoe UI'; " +
                        "-fx-font-size: 12px; " +
                        "-fx-cursor: hand;"
        );

        // 悬停效果
        button.setOnMouseEntered(e -> button.setStyle(
                "-fx-background-color: derive(" + color + ", -10%); " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 4 8; " +
                        "-fx-background-radius: 3; " +
                        "-fx-font-family: 'Segoe UI'; " +
                        "-fx-font-size: 12px; " +
                        "-fx-cursor: hand;"
        ));

        button.setOnMouseExited(e -> button.setStyle(
                "-fx-background-color: " + color + "; " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 4 8; " +
                        "-fx-background-radius: 3; " +
                        "-fx-font-family: 'Segoe UI'; " +
                        "-fx-font-size: 12px; " +
                        "-fx-cursor: hand;"
        ));
    }

    // 美化列表视图
    private void styleListView(ListView<?> listView) {
        listView.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-radius: 4; " +
                        "-fx-control-inner-background: white; " +
                        "-fx-text-fill: #495057; " +
                        "-fx-padding: 3;"
        );
    }

    // 美化下拉框
    private void styleComboBox(ComboBox<?> comboBox) {
        comboBox.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-radius: 3; " +
                        "-fx-text-fill: #495057; " +
                        "-fx-padding: 2 4; " +
                        "-fx-font-family: 'Segoe UI'; " +
                        "-fx-font-size: 12px;"
        );
    }

    // 美化文本框
    private void styleTextField(TextField textField) {
        textField.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-radius: 3; " +
                        "-fx-text-fill: #495057; " +
                        "-fx-prompt-text-fill: #6c757d; " +
                        "-fx-font-family: 'Segoe UI'; " +
                        "-fx-font-size: 12px;"
        );
    }

    // 美化对话框
    private void styleDialog(Dialog<?> dialog) {
        dialog.getDialogPane().setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-radius: 4;"
        );

        // 设置对话框文字颜色
        dialog.getDialogPane().getChildren().forEach(node -> {
            if (node instanceof Label) {
                ((Label) node).setTextFill(Color.rgb(33, 37, 41));
                ((Label) node).setFont(Font.font("Segoe UI", 12));
            }
        });

        // 按钮样式
        ButtonBar buttonBar = (ButtonBar)dialog.getDialogPane().lookup(".button-bar");
        buttonBar.getButtons().forEach(btn -> {
            Button button = (Button)btn;
            if (button.getText().equals("OK") || button.getText().equals("Add")) {
                styleButton(button, "#2196f3");
            } else if (button.getText().equals("Cancel")) {
                styleButton(button, "#6c757d");
            }
        });
    }

    // 显示提示对话框
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleDialog(alert);
        alert.showAndWait();
    }

    // 显示可编辑值的对话框
    private String showEditDialog(String title, String currentValue) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);

        // 设置按钮
        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        // 创建输入框
        TextField inputField = new TextField(currentValue);
        styleTextField(inputField);
        inputField.setPrefWidth(200);

        dialog.getDialogPane().setContent(inputField);

        // 按OK按钮时返回输入值
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return inputField.getText();
            }
            return null;
        });

        styleDialog(dialog);
        Optional<String> result = dialog.showAndWait();
        return result.orElse(currentValue);
    }

    // 代理组数据模型
    public static class ProxyGroup {
        private String name;
        private String type;

        public ProxyGroup(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() { return name; }
        public String getType() { return type; }
    }

    // 代理节点数据模型
    public static class ProxyNode {
        private String name;
        private String type;
        private String latency;

        public ProxyNode(String name, String type, String latency) {
            this.name = name;
            this.type = type;
            this.latency = latency;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getLatency() { return latency; }
    }

    // 代理节点块状组件 - type和latency在底部平行显示
    private class ProxyNodeBlock extends BorderPane {
        private ProxyNode node;
        private boolean isSelected;
        private ProxyGroup group;

        public ProxyNodeBlock(ProxyNode node, ProxyGroup group) {
            this.node = node;
            this.group = group;
            this.isSelected = false;

            // 检查是否是已选中的节点
            String selectedNode = selectedNodeMap.get(group.getName());
            if (selectedNode != null && selectedNode.equals(node.getName())) {
                this.isSelected = true;
            }

            initUI();

            // 添加悬停动画效果
            setOnMouseEntered(e -> {
                if (!isSelected) {
                    setStyle(
                            "-fx-background-color: #f8f9fa; " +
                                    "-fx-border-color: #d0d7dc; " +
                                    "-fx-border-radius: 4; " +
                                    "-fx-padding: 10; " +
                                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 3, 0, 0, 1); " +
                                    "-fx-translate-y: -1px;"
                    );
                }
            });

            setOnMouseExited(e -> {
                if (!isSelected) {
                    updateSelectedStyle();
                }
            });
        }

        private void initUI() {
            // 初始样式
            updateSelectedStyle();

            // 节点名称
            Label nameLabel = new Label(node.getName());
            nameLabel.setFont(Font.font("Segoe UI", 13));
            nameLabel.setTextFill(Color.rgb(33, 37, 41));
            nameLabel.setStyle("-fx-font-weight: 500;");
            nameLabel.setPadding(new Insets(0, 0, 5, 0));

            // 底部内容容器，包含type和latency
            HBox bottomContent = new HBox();
            bottomContent.setSpacing(10);

            // 左侧放置type（左下角）
            Label typeLabel = new Label("Type: " + node.getType());
            typeLabel.setFont(Font.font("Segoe UI", 11));
            typeLabel.setTextFill(Color.rgb(73, 80, 87));
            typeLabel.setAlignment(Pos.CENTER_LEFT);

            // 填充空间
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 右侧放置latency（右下角）
            Label latencyLabel = new Label("Latency: " + node.getLatency());
            latencyLabel.setFont(Font.font("Segoe UI", 11));

            // 根据延迟设置颜色
            try {
                String latencyStr = node.getLatency().replace("ms", "").trim();
                int latency = Integer.parseInt(latencyStr);
                if (latency < 100) {
                    latencyLabel.setTextFill(Color.rgb(76, 175, 80)); // 绿色
                } else if (latency < 300) {
                    latencyLabel.setTextFill(Color.rgb(255, 152, 0)); // 橙色
                } else {
                    latencyLabel.setTextFill(Color.rgb(244, 67, 54)); // 红色
                }
            } catch (NumberFormatException e) {
                latencyLabel.setTextFill(Color.rgb(73, 80, 87));
            }

            bottomContent.getChildren().addAll(typeLabel, spacer, latencyLabel);

            // 组织布局
            VBox content = new VBox(5);
            content.getChildren().addAll(nameLabel, bottomContent);

            setCenter(content);
            setPadding(new Insets(10));

            // 添加点击事件和动画
            setOnMouseClicked(e -> {
                selectNode();
                e.consume();
            });

            setCursor(javafx.scene.Cursor.HAND);

            // 添加轻微的点击动画
            setOnMousePressed(e -> {
                if (!isSelected) {
                    setStyle(
                            "-fx-background-color: #f1f1f1; " +
                                    "-fx-border-color: #d0d7dc; " +
                                    "-fx-border-radius: 4; " +
                                    "-fx-padding: 10; " +
                                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 2, 0, 0, 1); " +
                                    "-fx-translate-y: 0px;"
                    );
                }
            });

            setOnMouseReleased(e -> {
                if (!isSelected) {
                    setStyle(
                            "-fx-background-color: #f8f9fa; " +
                                    "-fx-border-color: #d0d7dc; " +
                                    "-fx-border-radius: 4; " +
                                    "-fx-padding: 10; " +
                                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 3, 0, 0, 1); " +
                                    "-fx-translate-y: -1px;"
                    );
                }
            });
        }

        // 选中节点
        public void selectNode() {
            // 添加选中动画
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.millis(150),
                            new KeyValue(opacityProperty(), 0.5, Interpolator.EASE_BOTH)
                    ),
                    new KeyFrame(Duration.millis(300),
                            new KeyValue(opacityProperty(), 1, Interpolator.EASE_BOTH)
                    )
            );

            // 更新选中状态
            isSelected = true;
            selectedNodeMap.put(group.getName(), node.getName());

            // 刷新列表以更新其他节点的选中状态
            groupListView.refresh();

            timeline.play();
        }

        // 更新选中样式
        private void updateSelectedStyle() {
            if (isSelected) {
                setStyle(
                        "-fx-background-color: #f0f7ff; " +
                                "-fx-border-color: #90caf9; " +
                                "-fx-border-radius: 4; " +
                                "-fx-padding: 10; " +
                                "-fx-effect: dropshadow(three-pass-box, rgba(33, 150, 243, 0.1), 3, 0, 0, 1);"
                );
            } else {
                setStyle(
                        "-fx-background-color: white; " +
                                "-fx-border-color: #e0e0e0; " +
                                "-fx-border-radius: 4; " +
                                "-fx-padding: 10; " +
                                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 2, 0, 0, 1);"
                );
            }
        }

        public boolean isSelected() {
            return isSelected;
        }

        public ProxyNode getNode() {
            return node;
        }
    }

    // 创建其他面板
    private VBox createProfilesPanel() {
        VBox panel = createPanelContainer("Profile Management");

        // 添加配置文件按钮
        Button addProfileBtn = new Button("Add Profile");
        styleButton(addProfileBtn, "#2196f3");
        addProfileBtn.setPrefSize(100, 28);

        // 配置文件列表
        ListView<String> profileListView = new ListView<>();
        styleListView(profileListView);
        profileListView.setPrefHeight(350);

        ObservableList<String> profiles = FXCollections.observableArrayList(
                "Default Profile", "Work Profile", "Personal Profile"
        );
        profileListView.setItems(profiles);

        // 添加列表项样式
        profileListView.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setPadding(new Insets(8, 5, 8, 12));
                    if (isSelected()) {
                        setStyle(
                                "-fx-background-color: #e3f2fd; " +
                                        "-fx-text-fill: #2196f3; " +
                                        "-fx-font-family: 'Segoe UI'; " +
                                        "-fx-font-size: 12px;"
                        );
                    } else {
                        setStyle(
                                "-fx-background-color: white; " +
                                        "-fx-text-fill: #495057; " +
                                        "-fx-font-family: 'Segoe UI'; " +
                                        "-fx-font-size: 12px;"
                        );
                    }
                }
            }
        });

        panel.getChildren().addAll(addProfileBtn, profileListView);
        return panel;
    }

    private VBox createSettingsPanel() {
        VBox panel = createPanelContainer("Application Settings");

        VBox appSettings = createSettingSection("Application");
        appSettings.getChildren().addAll(
                createToggleSetting("Start with Windows", true),
                createToggleSetting("Minimize to tray", true),
                createToggleSetting("Auto-check updates", true)
        );

        VBox themeSettings = createSettingSection("Theme");
        ComboBox<String> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll("Light", "Dark", "System");
        themeCombo.setValue("Light");
        styleComboBox(themeCombo);
        themeSettings.getChildren().add(themeCombo);

        panel.getChildren().addAll(appSettings, themeSettings);
        return panel;
    }

    private VBox createLogsPanel() {
        VBox panel = createPanelContainer("Connection Logs");

        // 日志级别选择
        HBox logLevelBox = new HBox(8);
        logLevelBox.setAlignment(Pos.CENTER_LEFT);
        logLevelBox.setPadding(new Insets(0, 0, 8, 0));

        Label logLevelLabel = new Label("Log Level:");
        logLevelLabel.setTextFill(Color.rgb(33, 37, 41));
        logLevelLabel.setFont(Font.font("Segoe UI", 12));

        ComboBox<String> logLevelCombo = new ComboBox<>();
        logLevelCombo.getItems().addAll("Debug", "Info", "Warning", "Error");
        logLevelCombo.setValue("Info");
        styleComboBox(logLevelCombo);
        logLevelCombo.setMaxWidth(100);

        Button clearLogsBtn = new Button("Clear Logs");
        styleButton(clearLogsBtn, "#6c757d");
        clearLogsBtn.setPrefSize(80, 28);

        logLevelBox.getChildren().addAll(logLevelLabel, logLevelCombo, clearLogsBtn);

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
        logArea.setText("[INFO] Connected to proxy server\n" +
                "[INFO] Proxy mode: Rule\n" +
                "[INFO] System proxy enabled\n");
        logArea.setEditable(false);

        panel.getChildren().addAll(logLevelBox, logArea);
        return panel;
    }

    // 创建设置区域
    private VBox createSettingSection(String title) {
        VBox section = new VBox();

        Label sectionTitle = new Label(title);
        sectionTitle.setTextFill(Color.rgb(73, 80, 87));
        sectionTitle.setFont(Font.font("Segoe UI", 13));
        sectionTitle.setStyle(
                "-fx-padding: 5 0; " +
                        "-fx-border-color: #e1e4e8; " +
                        "-fx-border-width: 0 0 1 0;"
        );

        section.getChildren().add(sectionTitle);
        return section;
    }

    // 创建可编辑的值行（使用下划线样式的Label替代TextField）
    private HBox createEditableValueRow(String label, String value) {
        HBox row = new HBox();
        row.setSpacing(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));

        Label nameLabel = new Label(label);
        nameLabel.setTextFill(Color.rgb(33, 37, 41));
        nameLabel.setFont(Font.font("Segoe UI", 12));
        nameLabel.setMinWidth(100);

        // 使用带下划线的Label替代TextField
        Label valueLabel = new Label(value);
        valueLabel.setTextFill(Color.rgb(33, 150, 243)); // 蓝色
        valueLabel.setFont(Font.font("Segoe UI", 12));
        valueLabel.setStyle(
                "-fx-border-color: #3399ff; " +
                        "-fx-border-width: 0 0 1 0; " +
                        "-fx-border-style: dashed; " +
                        "-fx-cursor: hand;"
        );

        // 添加点击事件，弹出编辑对话框
        valueLabel.setOnMouseClicked(e -> {
            String newValue = showEditDialog("Edit " + label, valueLabel.getText());
            valueLabel.setText(newValue);
        });

        row.getChildren().addAll(nameLabel, valueLabel);
        return row;
    }

    // 创建开关设置
    private HBox createToggleSetting(String label, boolean defaultValue) {
        HBox row = new HBox();
        row.setSpacing(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));

        Label nameLabel = new Label(label);
        nameLabel.setTextFill(Color.rgb(33, 37, 41));
        nameLabel.setFont(Font.font("Segoe UI", 12));
        nameLabel.setMinWidth(100);

        ToggleSwitch toggle = new ToggleSwitch();
        toggle.setSelected(defaultValue);

        row.getChildren().addAll(nameLabel, toggle);
        return row;
    }

    // 创建带有值的开关设置
    private HBox createToggleSetting(String label, String value) {
        HBox row = new HBox();
        row.setSpacing(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));

        Label nameLabel = new Label(label);
        nameLabel.setTextFill(Color.rgb(33, 37, 41));
        nameLabel.setFont(Font.font("Segoe UI", 12));
        nameLabel.setMinWidth(100);

        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll("info", "warning", "error", "debug");
        combo.setValue(value);
        styleComboBox(combo);

        row.getChildren().addAll(nameLabel, combo);
        return row;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// 速率显示独立组件 - 优化为更精小
class RateDisplay extends HBox {
    private Label uploadLabel;
    private Label downloadLabel;

    public RateDisplay() {
        init();
    }

    private void init() {
        this.setSpacing(6); // 减小间距
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(2));

        uploadLabel = new Label("↑ 0.00 KB/s");
        uploadLabel.setTextFill(Color.rgb(76, 175, 80));
        uploadLabel.setFont(Font.font("Segoe UI", 11)); // 减小字体

        downloadLabel = new Label("↓ 0.00 KB/s");
        downloadLabel.setTextFill(Color.rgb(33, 150, 243));
        downloadLabel.setFont(Font.font("Segoe UI", 11)); // 减小字体

        this.getChildren().addAll(uploadLabel, downloadLabel);
    }

    public void updateRates(double uploadRate, double downloadRate) {
        uploadLabel.setText(String.format("↑ %.2f KB/s", uploadRate));
        downloadLabel.setText(String.format("↓ %.2f KB/s", downloadRate));
    }
}

// 自定义开关控件
class ToggleSwitch extends Region {
    private final Circle toggleKnob;
    private boolean selected;

    // 颜色常量
    private static final Color BACKGROUND_ON = Color.rgb(66, 153, 225);
    private static final Color BACKGROUND_OFF = Color.rgb(204, 204, 204);
    private static final Color KNOB_COLOR = Color.WHITE;
    private static final Color KNOB_SHADOW = Color.rgb(0, 0, 0, 0.2);

    // 尺寸常量
    private static final double WIDTH = 40;
    private static final double HEIGHT = 22;
    private static final double KNOB_RADIUS = 9;
    private static final double KNOB_MARGIN = 2;

    public ToggleSwitch() {
        // 初始化开关按钮
        toggleKnob = new Circle(KNOB_RADIUS);
        toggleKnob.setFill(KNOB_COLOR);
        toggleKnob.setEffect(new DropShadow(1, KNOB_SHADOW));

        // 设置初始状态
        selected = false;
        updateVisualState(false);

        // 添加点击事件
        setOnMouseClicked(e -> {
            selected = !selected;
            updateVisualState(true);
        });

        // 设置控件尺寸
        setPrefSize(WIDTH, HEIGHT);
        getChildren().add(toggleKnob);

        // 添加样式
        setStyle("-fx-cursor: hand;");
    }

    // 更新视觉状态，带动画效果
    private void updateVisualState(boolean animate) {
        // 设置背景色
        setBackground(new Background(new BackgroundFill(
                selected ? BACKGROUND_ON : BACKGROUND_OFF,
                new CornerRadii(HEIGHT / 2),
                Insets.EMPTY
        )));

        // 计算开关位置
        double targetX = selected ?
                WIDTH - KNOB_RADIUS - KNOB_MARGIN :
                KNOB_RADIUS + KNOB_MARGIN;

        if (animate) {
            // 添加平滑过渡动画
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.millis(150),
                            new KeyValue(toggleKnob.centerXProperty(), targetX, Interpolator.EASE_BOTH)
                    )
            );
            timeline.play();
        } else {
            toggleKnob.setCenterX(targetX);
        }

        // 始终保持垂直居中
        toggleKnob.setCenterY(HEIGHT / 2);
    }

    public void setSelected(boolean value) {
        if (selected != value) {
            selected = value;
            updateVisualState(true);
        }
    }

    public boolean isSelected() {
        return selected;
    }

    // 确保控件大小正确
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        updateVisualState(false);
    }
}
