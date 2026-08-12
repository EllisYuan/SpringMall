---
description: MyBatis XML Mapper 规约（参数化、ResultMap、乐观锁）
paths:
  - "mall-backend/src/main/resources/mapper/**"
---

# MyBatis Mapper 规约

## Mapper 接口（`mapper/FeatureMapper.java`）
```java
@Mapper
public interface FeatureMapper {
    Feature findById(@Param("id") Long id);
    int insert(Feature feature);
    int updateById(Feature feature);
    int deleteById(@Param("id") Long id);
}
```

## XML（`src/main/resources/mapper/FeatureMapper.xml`）
- 必须包含 `BaseResultMap` 与 `Base_Column_List` SQL 片段。
- 列名用 `snake_case`，映射到 `camelCase` 字段。

## SQL 注入红线（BLOCKER）
- 所有参数值用 `#{}` 参数化占位。
- **绝不**对用户输入使用 `${}` —— 那是 SQL 注入漏洞。
  - 危险：`WHERE name = '${name}'`
  - 安全：`WHERE name = #{name}`
- 唯一可接受的 `${}`：来自硬编码枚举的结构元素（表名/列名），且必须注释说明理由。

## 库存等并发写：乐观锁模式
```xml
UPDATE product SET stock = stock - #{quantity}
WHERE id = #{id} AND stock >= #{quantity}
```
依据 `affected rows` 判断是否成功，避免超卖。
