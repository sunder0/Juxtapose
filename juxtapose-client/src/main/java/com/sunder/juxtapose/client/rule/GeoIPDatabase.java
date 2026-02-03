package com.sunder.juxtapose.client.rule;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.StreamProgress;
import cn.hutool.cron.CronUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpUtil;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.common.MultiProtocolResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;

/**
 * @author : sunder
 * @date : 11:16 2025/09/10
 *         构建GeoIP数据库， mmdb文件参照：https://github.com/Dreamacro/maxmind-geoip/releases/latest/download/Country.mmdb
 */
public class GeoIPDatabase {
    private final static String MMDB_TEM_PATH = "conf/new.mmdb";
    private final Logger logger = LoggerFactory.getLogger(GeoIPDatabase.class);
    private final ClientConfig ccfg;
    private DatabaseReader geoIPReader;

    public GeoIPDatabase(ClientConfig ccfg) throws Exception {
        this.ccfg = ccfg;
        geoIPReader = new DatabaseReader.Builder(new File(ccfg.getGeoIPPath())).build();

        // 设置匹配秒级, 每周周一10点执行
        CronUtil.setMatchSecond(true);
        CronUtil.schedule("0 0 10 ? * MON", (Runnable) () -> pullGeoIpCountryMmdb(ccfg.getGeoIPUrl()));
        CronUtil.start();
        Runtime.getRuntime().addShutdownHook(new Thread(CronUtil::stop));
    }

    /**
     * 查询ip地址国家代码
     *
     * @param inetAddress ip地址
     * @return 如果是内网，会报错，默认返回CN
     */
    public String country(InetAddress inetAddress) {
        try {
            CountryResponse response = geoIPReader.country(inetAddress);
            return response.getCountry().getIsoCode();
        } catch (Exception ex) {
            if (inetAddress == null) {
                logger.warn("Query GeoIp db error, inetAddress is null.", ex);
                return "CN";
            }
            logger.warn("Query GeoIp db error[{}], [{}].", inetAddress, ex.getMessage());
            return "CN";
        }
    }

    /**
     * 拉取最新的mmdb文件
     *
     * @param mmdbUrl mmdburl，
     */
    private void pullGeoIpCountryMmdb(String mmdbUrl) {
        MultiProtocolResource resource = new MultiProtocolResource(MMDB_TEM_PATH, true);
        try {
            File destFile = new File(resource.getResource().getUrl().getPath());
            HttpUtil.downloadFile(mmdbUrl, destFile, new GeoIpDownloadProgress(ccfg.getGeoIPUrl()));

            if (destFile.exists() && isGeoFileSame(ccfg.getGeoIPPath(), destFile)) {
                FileUtil.rename(destFile, ccfg.getGeoIPPath(), true);

                this.geoIPReader.close();
                this.geoIPReader = new DatabaseReader.Builder(new File(ccfg.getGeoIPPath())).build();
            } else {
                FileUtil.del(destFile);
            }
        } catch (Exception ex) {
            logger.error("Pull GeoIp mmdb file error[{}]!", ex.getMessage());
        }
    }


    /**
     * 是否geo mmdb文件相同，md5判断
     */
    private boolean isGeoFileSame(String originPath, File destFile) {
        return DigestUtil.md5Hex(FileUtil.readBytes(originPath)).equals(DigestUtil.md5Hex(destFile));
    }

    /**
     * geo文件下载进度
     */
    class GeoIpDownloadProgress implements StreamProgress {
        private String mmdbUrl;

        public GeoIpDownloadProgress(String mmdbUrl) {
            this.mmdbUrl = mmdbUrl;
        }

        @Override
        public void start() {
            logger.info("Start pull GeoIp mmdb file[{}]...", mmdbUrl);
        }

        @Override
        public void progress(long currentSize, long totalSize) {
            if (totalSize > 0 && currentSize % (10 * 1024 * 1024) == 0) { // 每10MB打印一次
                logger.debug("Pull GeoIp mmdb progress: {}/{} MB",
                        currentSize / (1024 * 1024), totalSize / (1024 * 1024));
            }
        }

        @Override
        public void finish() {
            logger.info("Finished pull GeoIp mmdb file[{}]...", mmdbUrl);
        }
    }

}
