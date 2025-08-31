package com.sunder.juxtapose.common.id;

/**
 * @author : denglinhai
 * @date : 10:20 2025/08/28
 */
public interface IdGenerator {

    /**
     * 生成下一个long类型id
     *
     * @return long类型id
     */
    long nextId();

    /**
     * 生成字符串id
     *
     * @return string类型id
     */
    default String nextIdStr() {
        throw new UnsupportedOperationException();
    }

}
