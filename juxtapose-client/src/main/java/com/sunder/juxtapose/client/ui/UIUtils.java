package com.sunder.juxtapose.client.ui;

import com.sunder.juxtapose.client.ui.def.ToggleSwitch;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author : denglinhai
 * @date : 14:44 2025/09/22
 */
public class UIUtils {

    // 创建通用面板容器
    public static VBox createPanelContainer(String title) {
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

    // 创建设置区域
    public static VBox createSettingSection(String title) {
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
    public static HBox createEditableValueRow(String label, String value, Consumer<String> update) {
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
            update.accept(newValue);
        });

        row.getChildren().addAll(nameLabel, valueLabel);
        return row;
    }

    // 显示可编辑值的对话框
    public static String showEditDialog(String title, String currentValue) {
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

    // 显示提示对话框
    public static void showAlert(AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleDialog(alert);
        alert.showAndWait();
    }

    // 创建开关设置
    public static HBox createToggleSetting(String label, boolean defaultValue, Consumer<Boolean> uptCallback) {
        HBox row = new HBox();
        row.setSpacing(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));

        Label nameLabel = new Label(label);
        nameLabel.setTextFill(Color.rgb(33, 37, 41));
        nameLabel.setFont(Font.font("Segoe UI", 12));
        nameLabel.setMinWidth(100);

        ToggleSwitch toggle = new ToggleSwitch(defaultValue, uptCallback);
        // toggle.setSelected(defaultValue);

        row.getChildren().addAll(nameLabel, toggle);
        return row;
    }

    // 美化文本框
    public static void styleTextField(TextField textField) {
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

    // 美化下拉框
    public static void styleComboBox(ComboBox<?> comboBox) {
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

    // 美化对话框
    public static void styleDialog(Dialog<?> dialog) {
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
        ButtonBar buttonBar = (ButtonBar) dialog.getDialogPane().lookup(".button-bar");
        buttonBar.getButtons().forEach(btn -> {
            Button button = (Button) btn;
            if (button.getText().equals("OK") || button.getText().equals("Add")) {
                styleButton(button, "#2196f3");
            } else if (button.getText().equals("Cancel")) {
                styleButton(button, "#6c757d");
            }
        });
    }

    // 按钮样式设置
    public static void styleButton(Button button, String color) {
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
}
