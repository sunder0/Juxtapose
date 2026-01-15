package com.sunder.juxtapose.server.api;

import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.connection.ConnectionManager;
import com.sunder.juxtapose.server.connection.UpstreamConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author : denglinhai
 * @date : 15:44 2024/7/2
 */
@CrossOrigin
@RestController
@RequestMapping("/connection")
public class ConnectionController {
    private final Logger logger = LoggerFactory.getLogger(ConnectionController.class);
    @Resource
    private ApplicationContext applicationContext;

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<String> rmvConnection(@PathVariable("connectionId") String connectionId) {
        logger.info("Receive remove connection request[{}].", connectionId);
        ConnectionManager connectionManager = applicationContext.getBean(UpstreamConnectionManager.NAME,
                ConnectionManager.class);
        Connection connection = connectionManager.getConnection(connectionId);
        if (connection != null) {
            connection.close();
        }
        return ResponseEntity.ok("Connection remove successful!");
    }

}
