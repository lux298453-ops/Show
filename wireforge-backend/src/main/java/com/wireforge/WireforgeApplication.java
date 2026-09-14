package com.wireforge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.wireforge.mapper")
public class WireforgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(WireforgeApplication.class, args);
    }
}
