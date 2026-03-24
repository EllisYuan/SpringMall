package site.geekie.shop.shoppingmall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("site.geekie.shop.shoppingmall.mapper")
@EnableScheduling
public class ShoppingMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingMallApplication.class, args);
    }

}
