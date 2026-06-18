package com.traffic.alert.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "influxdb")
public class InfluxDBConfig {

    private boolean enabled = false;
    private String url;
    private String token;
    private String org;
    private String bucket;
    private String username;
    private String password;

    @Bean
    public InfluxDBClient influxDBClient() {
        if (!enabled) {
            log.info("InfluxDB is disabled by configuration (influxdb.enabled=false)");
            return null;
        }
        try {
            InfluxDBClient client = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
            if (client.ping()) {
                log.info("InfluxDB connected successfully: url={}, org={}, bucket={}", url, org, bucket);
                return client;
            } else {
                log.warn("InfluxDB ping failed, falling back to MySQL-only mode: url={}", url);
                client.close();
                return null;
            }
        } catch (Exception e) {
            log.warn("InfluxDB connection failed, falling back to MySQL-only mode: url={}, error={}", url, e.getMessage());
            return null;
        }
    }

    @Bean
    public WriteApiBlocking writeApiBlocking(InfluxDBClient influxDBClient) {
        if (influxDBClient == null) {
            return null;
        }
        return influxDBClient.getWriteApiBlocking();
    }
}
