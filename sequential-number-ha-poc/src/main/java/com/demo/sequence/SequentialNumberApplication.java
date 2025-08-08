package com.demo.sequence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Main Application Class for Sequential Number Generation HA POC
 * 
 * This application demonstrates:
 * - Global sequential invoice number generation
 * - High availability with etcd cluster
 * - Multi-site/partition support
 * - Gap management and recovery
 * - Real-time monitoring and demonstration
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SequentialNumberApplication implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(SequentialNumberApplication.class);

    public static void main(String[] args) {
        // Set system properties for portable execution
        System.setProperty("spring.main.banner-mode", "console");
        System.setProperty("logging.level.root", "INFO");
        System.setProperty("logging.pattern.console", 
            "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");

        // Start the application
        SpringApplication app = new SpringApplication(SequentialNumberApplication.class);
        app.run(args);
    }

    @Override
    public void onApplicationReady(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String[] activeProfiles = env.getActiveProfiles();
        
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.warn("Could not determine host address, using localhost");
        }

        logger.info(""); 
        logger.info("=================================================================");
        logger.info("  🎯 Sequential Number Generation HA POC Started Successfully!");
        logger.info("=================================================================");
        logger.info("");
        logger.info("  📊 Application Details:");
        logger.info("    • Profile(s): {}", String.join(", ", activeProfiles));
        logger.info("    • Port: {}", serverPort);
        logger.info("    • Host: {}", hostAddress);
        logger.info("");
        logger.info("  🌐 Access Points:");
        logger.info("    • Main Dashboard:    http://{}:{}{}/dashboard", hostAddress, serverPort, contextPath);
        logger.info("    • HA Monitoring:     http://{}:{}{}/ha-dashboard", hostAddress, serverPort, contextPath);
        logger.info("    • API Documentation: http://{}:{}{}/swagger-ui.html", hostAddress, serverPort, contextPath);
        logger.info("    • Health Check:      http://{}:{}{}/actuator/health", hostAddress, serverPort, contextPath);
        logger.info("    • Metrics:           http://{}:{}{}/actuator/metrics", hostAddress, serverPort, contextPath);
        logger.info("");
        logger.info("  🚀 Quick Start API Calls:");
        logger.info("    • Generate Sequence:  GET /api/v1/sequence/next?siteId=site-1&partitionId=partition-a&invoiceType=on-cycle");
        logger.info("    • View Statistics:    GET /api/v1/sequence/stats");
        logger.info("    • HA Demo:           POST /api/v1/ha-demo/failover");
        logger.info("");
        
        if (isHAProfile(activeProfiles)) {
            logger.info("  🔄 HA Cluster Information:");
            logger.info("    • Node ID: {}", env.getProperty("sequence.instance.id", "node-1"));
            logger.info("    • Cluster Nodes: 3 (Primary, Secondary, Tertiary)");
            logger.info("    • etcd Endpoints: {}", env.getProperty("sequence.etcd.endpoints", "embedded"));
            logger.info("    • Load Balancer: {}", env.getProperty("sequence.load-balancer.type", "embedded"));
            logger.info("");
        }
        
        logger.info("  📖 Documentation:");
        logger.info("    • Demo Guide: README.md");
        logger.info("    • API Reference: docs/API-REFERENCE.md");
        logger.info("    • Troubleshooting: docs/TROUBLESHOOTING.md");
        logger.info("");
        logger.info("=================================================================");
        logger.info("  Ready for demonstration! 🎉");
        logger.info("=================================================================");
        logger.info("");
    }

    private boolean isHAProfile(String[] profiles) {
        for (String profile : profiles) {
            if (profile.contains("ha")) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}