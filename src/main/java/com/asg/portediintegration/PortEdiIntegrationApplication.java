package com.asg.portediintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PortEdiIntegrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortEdiIntegrationApplication.class, args);
    }
}
