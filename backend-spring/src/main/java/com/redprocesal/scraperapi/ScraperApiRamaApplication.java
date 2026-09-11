package com.redprocesal.scraperapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

//Scrapper CC y NIT
@EnableCaching
@SpringBootApplication
public class ScraperApiRamaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScraperApiRamaApplication.class, args);
    }
}