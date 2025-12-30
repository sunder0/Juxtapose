package com.sunder.juxtapose.common.proxy;

/**
 * @author : sunder
 * @date : 16:20 2025/07/20
 */
public class ProxyMessageTransferException extends RuntimeException {
    public ProxyMessageTransferException() {
    }

    public ProxyMessageTransferException(String message) {
        super(message);
    }

    public ProxyMessageTransferException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProxyMessageTransferException(Throwable cause) {
        super(cause);
    }

    public ProxyMessageTransferException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
