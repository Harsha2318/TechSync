package com.techsync;

import java.sql.SQLException;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TechSyncWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechSyncWebApplication.class, args);
    }

    @Bean
    @SuppressWarnings("unused")
    CommandLineRunner initializeDatabase() {
        return args -> {
            try {
                DatabaseHelper.getConnection();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to initialize database", e);
            }
        };
    }
}
