package com.sunder.juxtapose.common.utils;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * @author : denglinhai
 * @date : 15:55 2025/09/26
 *         日志追加输出
 */
public class LogFileTailer {
    private final Logger logger;
    private final Path logFile;
    private volatile boolean running = true;
    private long lastPosition;
    private ExecutorService executor;

    public LogFileTailer(String filePath) {
        this.logger = LoggerFactory.getLogger(LogFileTailer.class);
        this.logFile = Paths.get(filePath);
        this.executor = Executors.newSingleThreadExecutor(
                ThreadFactoryBuilder.create().setNamePrefix("logs-tailer-").build());
    }

    public void start(Consumer<String> consumer) throws IOException, InterruptedException {
        // 1.先读取已存在的内容
        readExistingLogs(consumer);

        // 2. 启动监听线程
        executor.submit(() -> tailFile(consumer));

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            executor.shutdown();
        }));
    }

    /**
     * 读取已存在的日志
     *
     * @param consumer
     * @throws IOException
     */
    private void readExistingLogs(Consumer<String> consumer) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            String line;
            while ((line = file.readLine()) != null) {
                consumer.accept(line);
            }
            lastPosition = file.getFilePointer();
        }
    }

    private void tailFile(Consumer<String> consumer) {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path directory = logFile.getParent();
            directory.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            while (running) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        Path changedFile = (Path) event.context();
                        if (changedFile.equals(logFile.getFileName())) {
                            readNewLogLines(consumer);
                        }
                    }
                }
                key.reset();
            }
        } catch (Exception ex) {
            logger.error("log file tail error.", ex);
        }
    }

    private void readNewLogLines(Consumer<String> consumer) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            file.seek(lastPosition);

            String line;
            while ((line = file.readLine()) != null) {
                consumer.accept(line);
            }
            lastPosition = file.getFilePointer();
        }
    }
}
