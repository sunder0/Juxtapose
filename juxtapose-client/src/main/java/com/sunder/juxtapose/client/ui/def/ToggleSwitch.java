package com.sunder.juxtapose.client.ui.def;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * @author : denglinhai
 * @date : 17:08 2025/09/22
 *         自定义开关控件
 */
public class ToggleSwitch extends Region {
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

    public ToggleSwitch(boolean defaultVal, Consumer<Boolean> updCallback) {
        // 初始化开关按钮
        toggleKnob = new Circle(KNOB_RADIUS);
        toggleKnob.setFill(KNOB_COLOR);
        toggleKnob.setEffect(new DropShadow(1, KNOB_SHADOW));

        // 设置初始状态
        selected = defaultVal;
        updateVisualState(false);

        // 添加点击事件
        setOnMouseClicked(e -> {
            selected = !selected;
            updateVisualState(true);
            updCallback.accept(selected);
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
