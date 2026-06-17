package com.traffic.alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TrafficAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrafficAlertApplication.class, args);
    }

}
