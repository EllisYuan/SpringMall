package site.geekie.shop.shoppingmall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 雪花 ID 生成器配置属性
 *
 * 对应 application.yml 的 snowflake.* 命名空间，带安全默认值兜底。
 * 全局通用配置（不隶属秒杀模块），任何需要雪花主键的场景共用。
 */
@Data
@Component
@ConfigurationProperties(prefix = "snowflake")
public class SnowflakeProperties {

    /** 机器 ID（0-1023），单机部署默认 0；多实例部署时每个实例必须配置唯一值，否则会生成重复 ID */
    private long workerId = 0L;
}
