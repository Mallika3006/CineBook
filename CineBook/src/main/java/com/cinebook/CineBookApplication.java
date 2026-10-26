package com.cinebook;

import com.cinebook.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.TimeZone;

@SpringBootApplication
@EnableTransactionManagement
@EnableConfigurationProperties(AppProperties.class)
public class CineBookApplication {

    private final AppProperties appProperties;

    public CineBookApplication(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public static void main(String[] args) {
        SpringApplication.run(CineBookApplication.class, args);
    }

    @PostConstruct
    public void initTimezone() {
        String tz = appProperties.getTimezone() != null ? appProperties.getTimezone() : "Asia/Kolkata";
        TimeZone.setDefault(TimeZone.getTimeZone(tz));
    }
}
