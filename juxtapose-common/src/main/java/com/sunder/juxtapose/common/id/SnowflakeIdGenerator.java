package com.sunder.juxtapose.common.id;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * @author : denglinhai
 * @date : 10:24 2025/08/28
 *         雪花生成器
 */
public class SnowflakeIdGenerator implements IdGenerator {

    private final Snowflake snowflake;

    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        this.snowflake = IdUtil.createSnowflake(workerId, datacenterId);
    }

    @Override
    public long nextId() {
        return snowflake.nextId();
    }

}
