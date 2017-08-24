package edu.shu.yan.transclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TransclientApplication {
    private static final Logger log = LoggerFactory.getLogger(TransclientApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(TransclientApplication.class, args);
		System.out.println("hello world!");
		log.info("hello spring");
	}
}
