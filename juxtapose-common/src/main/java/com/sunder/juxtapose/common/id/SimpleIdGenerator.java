package com.sunder.juxtapose.common.id;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : denglinhai
 * @date : 17:00 2025/08/31
 */
public class SimpleIdGenerator implements IdGenerator {
    private final AtomicLong ID_GENERATOR;

    public SimpleIdGenerator() {
        this.ID_GENERATOR = new AtomicLong(0);
    }

    @Override
    public long nextId() {
        return ID_GENERATOR.incrementAndGet();
    }
}
