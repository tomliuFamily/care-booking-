package com.example.care;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.care.mybatis")

public class CareBookingBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(CareBookingBackendApplication.class, args);
	}

}
