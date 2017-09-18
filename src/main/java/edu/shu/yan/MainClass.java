package edu.shu.yan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import sun.applet.Main;

@SpringBootApplication
@EnableScheduling
public class MainClass {
    private static final Logger log = LoggerFactory.getLogger(MainClass.class);
    public static void main(String[] args) {
        SpringApplication.run(MainClass.class, args);
    }
}
