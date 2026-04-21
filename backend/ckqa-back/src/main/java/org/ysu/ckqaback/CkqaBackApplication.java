package org.ysu.ckqaback;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("org.ysu.ckqaback.mapper")
@SpringBootApplication
public class CkqaBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(CkqaBackApplication.class, args);
    }

}
