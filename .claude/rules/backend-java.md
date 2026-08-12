---
description: Spring Boot 后端 Java 编码规约（Controller/Service/Entity/分层模型/MapStruct/异常）
paths:
  - "mall-backend/src/main/java/**"
  - "mall-backend/pom.xml"
  - "mall-backend/src/main/resources/application*.yml"
---

# 后端 Java 编码规约

## Controller

按权限级别分三类，对应不同类级注解组合。

### A. 公开接口（无需认证）— 如 ProductController、AuthController
```java
@Tag(name = "Product", description = "商品接口")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated                              // 方法参数有 @Max 等约束时才加
public class ProductController {
    private final ProductService productService;

    @Operation(summary = "获取所有商品")
    @GetMapping
    public Result<PageResult<ProductVO>> getAllProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") @Max(100) int size) {
        return Result.success(productService.getAllProducts(page, size));
    }
}
```

### B. 用户认证接口（USER 角色）— 如 OrderController、CartController
```java
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")        // 类级统一鉴权
public class OrderController {
    @RateLimiter(count = 5, period = 60)
    @PostMapping
    public Result<OrderVO> createOrder(
            @Valid @RequestBody OrderDTO request,
            @Parameter(hidden = true) @CurrentUserId Long userId) {
        return Result.success(orderService.createOrder(request, userId));
    }
}
```

### C. 管理员接口（ADMIN 角色）— 路径前缀 `/api/v1/admin/...`，`@PreAuthorize("hasRole('ADMIN')")`

### 类级注解顺序（严格）
```
@Tag → @RestController → @RequestMapping → @RequiredArgsConstructor
→ @PreAuthorize（需鉴权时）→ @SecurityRequirement（可选）→ @Validated（有 @Max 等约束时）
```

### 方法级注解顺序（严格）
```
@Operation(summary) → @SecurityRequirement（可选）→ @RateLimiter（可选）→ @GetMapping/@PostMapping/...
```

### 关键规则
- 返回类型**始终**是 `Result<T>`；成功用 `Result.success(data)`。
- 业务错误**抛 `BusinessException`**，不要手动构造错误 `Result`。
- `@Tag.name` 用英文 PascalCase，`description` 用中文；`@Operation` 只写 `summary`（中文）。
- `@CurrentUserId Long userId` 前必须加 `@Parameter(hidden = true)`。
- `@PreAuthorize` 优先类级统一声明。`@RateLimiter` 用于敏感/高频操作（登录、注册、下单、购物车更新），`period` 单位秒。
- **禁止**从 Controller 直接返回实体（DO），必须转 VO。

## Service
- 接口在 `service/`，实现在 `service/impl/`。
- 多次 Mapper 写操作的方法标 `@Transactional`。
- 违反业务规则抛 `BusinessException(ResultCode.XXX)`。

## Entity（DO）
- 仅用 `@Data`；不用 `@Builder`、不用 `@AllArgsConstructor`。
- 类型约定：ID=`Long`、金额=`BigDecimal`、标记/计数=`Integer`、时间=`LocalDateTime`。
- 列名 `snake_case` ↔ 字段 `camelCase`（已开 `map-underscore-to-camel-case`）。
- 类名以 `DO` 结尾，位于 `entity/`。

## 分层领域模型（阿里规约）
| 对象 | 位置 | 职责 |
|---|---|---|
| DO | `entity/` | 与表一一对应，Mapper 向上传输 |
| DTO | `dto/` | 接收请求参数，带 Jakarta 校验注解，配合 `@Valid` |
| BO（按需） | `dto/bo/` | Service 输出的业务封装对象 |
| VO | `vo/` | 返回前端，普通 `@Data`，**必须过滤敏感字段（如密码）** |
| Query（按需） | `dto/query/` | 查询入参；超 2 参数必须封装为 Query，**禁止用 `Map` 传参** |

数据流：`Controller 收 DTO → Service 收 DTO/Query、将 DO 转 VO → Mapper 返 DO`。
- **DO 禁止越过 Service 边界**到 Controller；DTO/VO 不混用。

## 数据类型转换（MapStruct）
- 转换统一走 `converter/` 下 `XxxConverter` 接口，`@Mapper(componentModel = "spring")`。
- 方法命名：`toDO(DTO)`、`updateDOFromDTO(DTO, @MappingTarget DO)`、`toVO(DO)`、`toVOList(List<DO>)`。
- `id`、`createdAt`、`updatedAt` 及由 Service 决定的字段（password、role、status、userId 等）用 `@Mapping(ignore=true)`。
- **禁止**在 Service 中手写 `new DO()`+setter 链或私有 `convertToXxx()`。
- 例外（允许手动构建）：`OrderDO`/`OrderItemDO`（多数据源）、`PaymentDO`/`RefundDO`（第三方回调）。

## 异常处理
- 抛 `BusinessException(ResultCode.XXX)` → `GlobalExceptionHandler` 转 `Result.error(…)`，HTTP 200。
- Spring/认证异常返回真实 HTTP 码（400/401/403/500）。
- 新错误码在 `common/ResultCode.java` 按已有范围新增。
- **不要**静默吞异常（不记日志就 catch）。

## 编译验证
```bash
cd mall-backend ; .\mvnw.cmd compile
```
快速、无需数据库连接。

## 禁止事项
- 不在 Java 注解里写 SQL（SQL 全部写在 XML，见 `mybatis-mapper` 规则）。
- 未了解前端路由影响前，不改 `SecurityConfig` 的 URL 权限规则。
- 不碰 `.env`；新配置项加到 `application.yml` 并设合理默认值。
