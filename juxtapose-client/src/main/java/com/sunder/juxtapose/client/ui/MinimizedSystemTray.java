package com.sunder.juxtapose.client.ui;

import com.sunder.juxtapose.common.MultiProtocolResource;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;

/**
 * @author : denglinhai
 * @date : 10:05 2025/09/28
 *         系统托盘
 */
public class MinimizedSystemTray {
    private Logger logger;
    private volatile boolean isMinimizedToTray;
    private TrayIcon trayIcon;
    private Stage primaryStage;

    public MinimizedSystemTray(Stage primaryStage) {
        this.logger = LoggerFactory.getLogger(MinimizedSystemTray.class);
        this.primaryStage = primaryStage;
        this.isMinimizedToTray = false;
    }

    /**
     * 设置系统托盘
     */
    public void setupSystemTray() {
        if (!SystemTray.isSupported()) {
            logger.error("System not support tray!");
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        MultiProtocolResource resource = new MultiProtocolResource("conf/icon/Juxtapose_icon.jpg", true);
        Image image = Toolkit.getDefaultToolkit().getImage(resource.getResource().getUrl());

        // 创建弹出菜单
        PopupMenu popup = new PopupMenu();
        MenuItem openItem = new MenuItem("Dashboard");
        openItem.addActionListener(e -> Platform.runLater(this::restoreFromTray));
        MenuItem exitItem = new MenuItem("Quit");
        exitItem.addActionListener(e -> exitApp());
        popup.add(openItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(image, "Juxtapose", popup);
        trayIcon.setImageAutoSize(true);
        try {
            tray.add(trayIcon);
        } catch (AWTException ex) {
            logger.error("setup system tray error.", ex);
        }
    }

    /**
     * 缩小到系统盘
     */
    public void minimizeToTray(boolean close) {
        if (close) {
            exitApp();
        }
        // 设置为false时点击关闭按钮程序后整个Platform还在active状态不会退出
        Platform.setImplicitExit(false);
        if (isMinimizedToTray) {
            return;
        }

        Platform.runLater(() -> {
            primaryStage.hide();
            isMinimizedToTray = true;
        });
    }

    /**
     * 从系统盘恢复
     */
    public void restoreFromTray() {
        if (!isMinimizedToTray) {
            return;
        }
        primaryStage.show();
        primaryStage.toFront();
        primaryStage.setIconified(false);
        isMinimizedToTray = false;
    }

    /**
     * 退出应用
     */
    private void exitApp() {
        System.exit(0);
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        Platform.exit();
    }

}
