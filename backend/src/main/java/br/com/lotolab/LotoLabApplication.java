package br.com.lotolab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LotoLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(LotoLabApplication.class, args);
    }
}
