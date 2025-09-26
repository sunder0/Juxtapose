package com.sunder.juxtapose.client.ui.def;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * @author : denglinhai
 * @date : 17:08 2025/09/22
 *         速率显示独立组件
 */
public class RateDisplay extends HBox {
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
