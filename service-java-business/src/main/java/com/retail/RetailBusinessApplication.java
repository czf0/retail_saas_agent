package com.retail;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.retail.business.mapper", "com.retail.rbac.mapper"})
public class RetailBusinessApplication {
    public static void main(String[] args) {
        SpringApplication.run(RetailBusinessApplication.class, args);
    }
}
