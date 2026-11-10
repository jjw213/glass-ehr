package com.glassehr;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GlassEhrApplication {

    public static void main(String[] args) {
        // AGENTS.md: 날짜·시간은 Asia/Seoul 기준
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(GlassEhrApplication.class, args);
    }
}
