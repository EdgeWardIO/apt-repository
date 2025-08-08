package com.demo.sequence.config;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.test.EtcdCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * etcd Configuration for Sequential Number Generation
 * 
 * Sets up embedded etcd cluster for both POC and HA demonstrations.
 * Uses jetcd-test for embedded etcd server that works across platforms.
 */
@Configuration
public class EtcdConfig {

    private static final Logger logger = LoggerFactory.getLogger(EtcdConfig.class);

    @Value("${sequence.etcd.embedded:true}")
    private boolean embeddedMode;

    @Value("${sequence.etcd.data-directory:data/etcd-cluster}")
    private String dataDirectory;

    @Value("${sequence.etcd.client-port:2379}")
    private int clientPort;

    @Value("${sequence.etcd.peer-port:2380}")
    private int peerPort;

    private EtcdCluster etcdCluster;

    /**
     * Single-node etcd for basic POC
     */
    @Bean
    @Profile("poc")
    public Client etcdClientPoc() throws Exception {
        logger.info("Starting embedded etcd server for POC mode");
        
        try {
            etcdCluster = EtcdCluster.builder()
                    .withNodes(1)
                    .withDataDirectory(getDataDirectory())
                    .build();
            
            etcdCluster.start();
            
            String clientEndpoint = etcdCluster.getClientEndpoints().get(0).toString();
            logger.info("Embedded etcd server started successfully at: {}", clientEndpoint);
            
            Client client = Client.builder()
                    .endpoints(etcdCluster.getClientEndpoints())
                    .build();
                    
            logger.info("etcd client connected successfully");
            return client;
            
        } catch (Exception e) {
            logger.error("Failed to start embedded etcd server", e);
            throw new RuntimeException("Failed to initialize etcd", e);
        }
    }

    /**
     * Multi-node etcd cluster for HA POC
     */
    @Bean
    @Profile("ha-poc")
    public Client etcdClientHA() throws Exception {
        logger.info("Starting embedded etcd cluster for HA mode");
        
        try {
            // Create 3-node cluster for HA demonstration
            etcdCluster = EtcdCluster.builder()
                    .withNodes(3)
                    .withDataDirectory(getDataDirectory())
                    .build();
            
            etcdCluster.start();
            
            logger.info("Embedded etcd cluster started with {} nodes", etcdCluster.getClientEndpoints().size());
            etcdCluster.getClientEndpoints().forEach(endpoint -> 
                logger.info("etcd node available at: {}", endpoint)
            );
            
            Client client = Client.builder()
                    .endpoints(etcdCluster.getClientEndpoints())
                    .build();
                    
            logger.info("etcd cluster client connected successfully");
            return client;
            
        } catch (Exception e) {
            logger.error("Failed to start embedded etcd cluster", e);
            throw new RuntimeException("Failed to initialize etcd cluster", e);
        }
    }

    /**
     * External etcd client for production
     */
    @Bean
    @Profile("production")
    public Client etcdClientProduction(@Value("${sequence.etcd.endpoints}") String endpoints) {
        logger.info("Connecting to external etcd cluster: {}", endpoints);
        
        try {
            String[] endpointArray = endpoints.split(",");
            
            Client client = Client.builder()
                    .endpoints(endpointArray)
                    .build();
                    
            logger.info("Connected to external etcd cluster");
            return client;
            
        } catch (Exception e) {
            logger.error("Failed to connect to external etcd cluster", e);
            throw new RuntimeException("Failed to connect to etcd", e);
        }
    }

    private Path getDataDirectory() {
        String osName = System.getProperty("os.name").toLowerCase();
        Path baseDir;
        
        if (osName.contains("windows")) {
            baseDir = Paths.get(System.getProperty("user.home"), "AppData", "Local", "sequence-poc");
        } else {
            baseDir = Paths.get(System.getProperty("user.home"), ".sequence-poc");
        }
        
        Path fullPath = baseDir.resolve(dataDirectory);
        logger.info("etcd data directory: {}", fullPath);
        
        return fullPath;
    }

    @PreDestroy
    public void cleanup() {
        if (etcdCluster != null) {
            try {
                logger.info("Shutting down embedded etcd cluster");
                etcdCluster.close();
                logger.info("etcd cluster shutdown completed");
            } catch (Exception e) {
                logger.error("Error shutting down etcd cluster", e);
            }
        }
    }
}