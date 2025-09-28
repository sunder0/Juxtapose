package com.sunder.juxtapose.client.connection;

/**
 * @author : denglinhai
 * @date : 16:32 2025/09/16
 *         一些统计数据
 */
public class ConnectionStats {
    private long startTime;

    private long lastActivityTime;
    private long lastStateChangeTime;
    private long bytesUploaded; // 上传字节数, 单位：字节（B）
    private long bytesDownloaded; // 下载字节数, 单位：字节（B）

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public void setLastActivityTime(long lastActivityTime) {
        this.lastActivityTime = lastActivityTime;
    }

    public long getBytesUploaded() {
        return bytesUploaded;
    }

    public void setBytesUploaded(long bytesUploaded) {
        this.bytesUploaded = bytesUploaded;
    }

    public long getBytesDownloaded() {
        return bytesDownloaded;
    }

    public void setBytesDownloaded(long bytesDownloaded) {
        this.bytesDownloaded = bytesDownloaded;
    }

    public long getLastStateChangeTime() {
        return lastStateChangeTime;
    }

    public void setLastStateChangeTime(long lastStateChangeTime) {
        this.lastStateChangeTime = lastStateChangeTime;
    }
}
