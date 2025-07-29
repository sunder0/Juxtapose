package com.sunder.juxtapose.common;

/**
 * @author : denglinhai
 * @date : 16:24 2025/07/10
 */
public class LifecycleException extends RuntimeException {

    public LifecycleException(String message) {
        super(message);
    }

    public LifecycleException(String message, Throwable cause) {
        super(message, cause);
    }

}
