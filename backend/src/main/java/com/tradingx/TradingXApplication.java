package com.tradingx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TradingXApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingXApplication.class, args);
    }
}
