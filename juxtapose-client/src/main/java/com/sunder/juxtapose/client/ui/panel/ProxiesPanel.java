package com.sunder.juxtapose.client.ui.panel;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.sunder.juxtapose.client.SystemAppContext;
import com.sunder.juxtapose.client.conf.ProxyRuleConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeGroupConfig;
import com.sunder.juxtapose.client.ui.MainUIComponent;
import static com.sunder.juxtapose.client.ui.UIUtils.createPanelContainer;
import static com.sunder.juxtapose.client.ui.UIUtils.createSettingSection;
import static com.sunder.juxtapose.client.ui.UIUtils.showAlert;
import static com.sunder.juxtapose.client.ui.UIUtils.styleButton;
import static com.sunder.juxtapose.client.ui.UIUtils.styleTextField;
import com.sunder.juxtapose.common.BaseModule;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author : denglinhai
 * @date : 14:58 2025/09/22
 */
public class ProxiesPanel extends BaseModule<MainUIComponent> {
    private Logger logger = LoggerFactory.getLogger(ProxiesPanel.class);
    private VBox mainPane;
    private ProxyServerConfig pscfg;
    private ProxyRuleConfig prcfg;

    // Proxies相关组件
    private ListView<ProxyGroup> groupListView;
    private ObservableList<ProxyGroup> groups = FXCollections.observableArrayList();
    private Map<String, ObservableList<ProxyNode>> proxyNodesMap = new HashMap<>();
    private Map<String, String> selectedNodeMap; // 存储每个分组选中的节点
    private Map<String, Boolean> groupExpandedMap = new HashMap<>(); // 存储每个分组的展开状态

    public ProxiesPanel(MainUIComponent belongComponent, ProxyServerConfig pscfg, ProxyRuleConfig prcfg) {
        super("PROXIES_PANEL", belongComponent);
        this.pscfg = pscfg;
        this.prcfg = prcfg;

        initializeProxyData();
        initializeUI();
    }

    /**
     * 初始化代理数据
     */
    private void initializeProxyData() {
        // 赋值组列表
        for (ProxyServerNodeGroupConfig group : pscfg.getProxyNodeGroupConfigs()) {
            groups.add(new ProxyGroup(group.name, group.type));
        }
        // 初始化组的展开状态
        for (ProxyGroup group : groups) {
            groupExpandedMap.put(group.getName(), false);
        }

        // 赋值每个组节点列表
        Map<String, ProxyServerNodeConfig> proxyNodes = pscfg.getProxyNodeConfigs().stream()
                .collect(Collectors.toMap(e -> e.name, e -> e));
        for (ProxyServerNodeGroupConfig group : pscfg.getProxyNodeGroupConfigs()) {
            proxyNodesMap.computeIfAbsent(group.name, k -> FXCollections.observableArrayList());
            for (String nodeName : group.proxies) {
                ProxyServerNodeConfig nodeConfig = proxyNodes.get(nodeName);

                // 目前不支持组之间的跳转，todo
                if (nodeConfig == null) {
                    continue;
                }

                proxyNodesMap.get(group.name).add(new ProxyNode(nodeConfig.name, nodeConfig.type.name(), "10ms"));
            }
        }
        this.selectedNodeMap = SystemAppContext.CONTEXT.getSelectNodes();
    }

    /**
     * 初始化UI
     */
    private void initializeUI() {
        mainPane = createPanelContainer("Proxy Settings");

        // 顶部工具栏
        HBox toolbar = new HBox(8);
        toolbar.setPadding(new Insets(5, 0, 10, 0));

        // URL输入框
        TextField urlField = new TextField();
        urlField.setPromptText("Download from URL");
        styleTextField(urlField);
        urlField.setPrefWidth(320);

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

        groupListView = new ListView<>(groups);
        groupListView.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-border-width: 0; " +
                        "-fx-control-inner-background: transparent; " +
                        "-fx-text-fill: #495057; " +
                        "-fx-padding: 0;"
        );
        groupListView.setPrefHeight(350);

        // 添加选择监听器
        groupListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (oldVal != null) {
                groupListView.refresh();
            }
        });

        // 为代理组添加点击事件和样式
        groupListView.setCellFactory(param -> new GroupListCell());

        proxyGroupsSection.getChildren().add(groupListView);

        // 代理设置
        VBox proxySettings = createSettingSection("Proxy Settings");
        proxySettings.getChildren().addAll(
                createDirectorySettingRow("Profiles:", "Open File", pscfg.getConfigDirectory()),
                createDirectorySettingRow("Proxy Rules:", "Open File", prcfg.getConfigDirectory())
                // createEditableValueRow("Update Interval:", "3600", new Consumer<String>() {
                //     @Override
                //     public void accept(String s) {
                //
                //     }
                // })
        );

        mainPane.getChildren().addAll(toolbar, proxyGroupsSection, proxySettings);
        belongComponent.registerVbox("Proxies", mainPane);
    }

    // 创建目录选择设置行
    private HBox createDirectorySettingRow(String label, String value, File directory) {
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
        valueLabel.setOnMouseClicked(e -> openDirectory(directory));

        row.getChildren().addAll(nameLabel, valueLabel);
        return row;
    }

    // 从URL下载代理配置
    private void downloadFromUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            showAlert(AlertType.ERROR, "Error", "Please enter a valid URL");
            return;
        }

        SystemAppContext.CONTEXT.setProfileUrl(urlString);
        try (HttpResponse response = HttpUtil.createGet(urlString).execute()) {
            pscfg.loadYamlStream(response.bodyStream());

            // 清空现有数据
            clearAllProxyData();
            initializeProxyData();
            SystemAppContext.CONTEXT.truncateAndLoadProxySubscribers();

            showAlert(AlertType.INFORMATION, "Success", "Proxy configuration downloaded successfully");
            groupListView.refresh();
        } catch (Exception ex) {
            logger.error("download proxy from url error.", ex);
            showAlert(AlertType.ERROR, "Error", "Failed to download proxy configuration: " + ex.getMessage());
        }
    }

    // 从本地文件导入代理配置
    private void importFromFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Proxy Configuration");

        // 设置文件过滤器，只显示yaml文件
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(
                "Yaml files (*.yaml)", "*.yaml");
        fileChooser.getExtensionFilters().add(extFilter);

        // 获取当前舞台
        Stage stage = (Stage) mainPane.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            try (FileInputStream fis = new FileInputStream(selectedFile)) {
                pscfg.loadYamlStream(fis);

                clearAllProxyData();
                initializeProxyData();
                SystemAppContext.CONTEXT.truncateAndLoadProxySubscribers();

                showAlert(AlertType.INFORMATION, "Success",
                        "Proxy configuration imported successfully from " + selectedFile.getName());
                groupListView.refresh();
            } catch (IOException e) {
                showAlert(AlertType.ERROR, "Error", "Failed to import proxy configuration: " + e.getMessage());
            }
        }
    }

    // 打开目录
    private void openDirectory(File directory) {
        try {
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // 打开系统文件浏览器
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory);
            } else {
                showAlert(AlertType.ERROR, "Error", "Desktop is not supported on this system");
            }
        } catch (IOException ex) {
            showAlert(AlertType.ERROR ,"Error", "Failed to open directory: " + ex.getMessage());
        }
    }

    // 清空所有代理数据
    private void clearAllProxyData() {
        groups.clear();
        proxyNodesMap.clear();
        selectedNodeMap.clear();
        groupExpandedMap.clear();
    }


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
                                    new KeyValue(nodesContainer.opacityProperty(), expanded ? 1 : 0,
                                            Interpolator.EASE_BOTH)
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
                                    new KeyValue(expandIndicator.rotateProperty(), expanded ? 45 : 0,
                                            Interpolator.EASE_BOTH)
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
                ((Label) ((HBox) ((HBox) groupHeader.getChildren().get(0)).getChildren().get(1)).getChildren()
                        .get(0)).setRotate(expanded ? 45 : 0);
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

    // 代理组数据模型
    public static class ProxyGroup {
        private String name;
        private String type;

        public ProxyGroup(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
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

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getLatency() {
            return latency;
        }
    }

    // 代理节点块状组件
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

            // 只有select组才能选择节点
            if (group.type.equals("select")) {
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
            SystemAppContext.CONTEXT.addSelectNode(group.name, node.name);

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

}
