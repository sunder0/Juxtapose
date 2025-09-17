package com.sunder.juxtapose.client.rule;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

/**
 * @author : denglinhai
 * @date : 11:16 2025/09/10
 *         构建GeoIP数据库， mmdb文件参照：https://github.com/Dreamacro/maxmind-geoip/releases/latest/download/Country.mmdb
 */
public class GeoIPDatabase {
    private final Logger logger = LoggerFactory.getLogger(GeoIPDatabase.class);
    private final DatabaseReader geoIPReader;

    public GeoIPDatabase(String dbPath) throws IOException {
        File database = new File(dbPath);
        geoIPReader = new DatabaseReader.Builder(database).build();
    }

    /**
     * 查询ip地址国家代码
     * @param inetAddress ip地址
     * @return 如果是内网，会报错，默认返回CN
     */
    public String country(InetAddress inetAddress) {
        try {
            CountryResponse response = geoIPReader.country(inetAddress);
            return response.getCountry().getIsoCode();
        } catch (Exception ex) {
            logger.warn("Query GeoIp db error[{}], [{}].", inetAddress.toString(), ex.getMessage());
            return "CN";
        }
    }

}
