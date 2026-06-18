package com.traffic.alert.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "influxdb")
public class InfluxDBConfig {

    private boolean enabled = true;
    private String url;
    private String token;
    private String org;
    private String bucket;
    private String username;
    private String password;

    @Bean
    public InfluxDBClient influxDBClient() {
        if (!enabled) {
            return null;
        }
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }

    @Bean
    public WriteApiBlocking writeApiBlocking(InfluxDBClient influxDBClient) {
        if (influxDBClient == null) {
            return null;
        }
        return influxDBClient.getWriteApiBlocking();
    }
}
