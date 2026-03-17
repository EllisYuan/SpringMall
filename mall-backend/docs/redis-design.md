# Redis 缓存设计

## 目录

- [全部 Key 清单](#全部-key-清单)
- [商品缓存](#商品缓存)
- [库存缓存](#库存缓存)
- [分布式锁](#分布式锁)
- [Token 黑名单](#token-黑名单)
- [用户认证缓存](#用户认证缓存)
- [接口限流](#接口限流)
- [降级策略](#降级策略)
- [Redis 配置说明](#redis-配置说明)

---

## 全部 Key 清单

| Key 前缀 | 数据结构 | TTL | 用途 | 来源类 |
|---------|---------|-----|------|--------|
| `cache:product:{productId}` | String（JSON）| 30 分钟 | 商品对象缓存 | `ProductCacheService` |
| `cache:product:null:{productId}` | String（"1"）| 5 分钟 | 空值防穿透标记 | `ProductCacheService` |
| `stock:product:{productId}` | String（整数）| 24 小时 | 商品库存数量 | `StockRedisService` |
| `lock:{业务}:{标识}` | String（UUID）| 业务指定 | 分布式锁标记 | `RedisDistributedLock` |
| `auth:blacklist:{tokenHash}` | String（"1"）| token 剩余有效期 | Token 黑名单 | `TokenBlacklistService` |
| `auth:force-logout:{userId}` | String（时间戳）| 24 小时 | 用户强制登出标记 | `TokenBlacklistService` |
| `auth:user:{userId}` | String（JSON）| 30 分钟 | 用户认证信息缓存 | `UserAuthCacheService` |
| `ratelimit:{identity}:{key}` | String（计数器）| 限流窗口期 | 接口调用次数计数 | `RateLimiterAspect` |

---

## 商品缓存

**实现类：** `util/ProductCacheService.java`

### 读取策略

```
请求商品 productId
    │
    ▼
检查空值缓存 cache:product:null:{productId}
    │ 命中 → 返回 null（商品不存在）
    │ 未命中 ↓
检查正常缓存 cache:product:{productId}
    │ 命中 → 反序列化 JSON → 返回 ProductDO
    │         反序列化失败 → 删除脏缓存，返回 null（触发回源）
    │ 未命中 ↓
查询数据库
    │ 存在 → putProduct() 写入正常缓存（TTL=30min）→ 返回
    │ 不存在 → putNull() 写入空值缓存（TTL=5min）→ 返回 null
```

### 空值防穿透

对数据库中不存在的商品 ID，缓存 `"1"` 字符串到 `cache:product:null:{productId}`，TTL 5 分钟，防止频繁查询不存在的 ID 打穿数据库：

```java
// ProductCacheService.java
public ProductDO getProduct(Long productId) {
    // 优先检查空值缓存（防穿透）
    String nullKey = NULL_PREFIX + productId;
    if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(nullKey))) {
        return null; // 已知不存在，直接返回
    }
    // ... 查正常缓存 ...
}

public void putNull(Long productId) {
    stringRedisTemplate.opsForValue()
        .set(NULL_PREFIX + productId, "1", 5, TimeUnit.MINUTES);
}
```

### 反序列化失败自清除

若 Redis 中存储的 JSON 与当前 `ProductDO` 结构不兼容（如字段变更），反序列化失败时自动删除脏缓存，触发下一次请求回源：

```java
try {
    return objectMapper.readValue(json, ProductDO.class);
} catch (Exception e) {
    log.warn("反序列化商品缓存异常 - productId: {}", productId, e);
    stringRedisTemplate.delete(key); // 自清除脏数据
    return null;
}
```

### 缓存清除时机

| 触发场景 | 调用方法 |
|---------|---------|
| 管理员修改商品信息 | `evictProduct(productId)` |
| 管理员下架商品 | `evictProduct(productId)` |
| 管理员删除商品 | `evictProduct(productId)` |

---

## 库存缓存

**实现类：** `util/StockRedisService.java`

**Key 格式：** `stock:product:{productId}`，String 类型，值为库存整数字符串。

### Lua 脚本批量预扣

两轮循环保证原子性：先校验全部，再扣减全部：

```lua
-- BATCH_DEDUCT_SCRIPT
local n = #KEYS
-- 第一轮：全部校验
for i = 1, n do
    local stock = tonumber(redis.call('get', KEYS[i]))
    if stock == nil then return -1 end   -- 缓存未加载，需懒加载后重试
    if stock < tonumber(ARGV[i]) then return -2 end  -- 库存不足
end
-- 第二轮：全部扣减（只有通过第一轮校验才执行）
for i = 1, n do
    redis.call('decrby', KEYS[i], ARGV[i])
    redis.call('expire', KEYS[i], 86400)  -- 刷新 TTL
end
return 1  -- 成功
```

**返回值：**
- `1`：批量预扣成功
- `-1`：有商品缓存未加载（调用方需懒加载后重试）
- `-2`：有商品库存不足

### 懒加载 SETNX

当 `batchDeductStock` 返回 `-1` 时，调用方触发懒加载，使用 `SETNX`（`setIfAbsent`）防止并发重复写入：

```java
// StockRedisService.loadStockIfAbsent()
public void loadStockIfAbsent(Long productId) {
    String key = STOCK_KEY_PREFIX + productId;
    ProductDO product = productMapper.findById(productId);
    if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
        return; // 商品不存在或已下架，不加载
    }
    // SETNX：key 不存在时才设置，防止并发覆盖
    Boolean loaded = stringRedisTemplate.opsForValue()
        .setIfAbsent(key, String.valueOf(product.getStock()), 24, TimeUnit.HOURS);
}
```

### 批量恢复库存

订单取消/支付超时时，恢复已预扣的 Redis 库存：

```lua
-- BATCH_RESTORE_SCRIPT：仅对存在的 key 执行 INCRBY
for i = 1, #KEYS do
    if redis.call('exists', KEYS[i]) == 1 then
        redis.call('incrby', KEYS[i], ARGV[i])
        redis.call('expire', KEYS[i], 86400)
    end
    -- key 不存在：缓存已过期，无需恢复，下次下单时懒加载即可
end
return 1
```

---

## 分布式锁

**实现类：** `util/RedisDistributedLock.java`

### 加锁

使用 `SET key value NX EX` 原子操作（`setIfAbsent`），成功返回 UUID 锁值：

```java
public String tryLock(String key, long timeout, TimeUnit unit) {
    String value = UUID.randomUUID().toString();
    Boolean success = stringRedisTemplate.opsForValue()
        .setIfAbsent(key, value, timeout, unit);
    return Boolean.TRUE.equals(success) ? value : null;
}
```

### 释放锁（Lua 原子比较删除）

防止线程 A 锁超时后，线程 B 获取了锁，线程 A 误删线程 B 的锁：

```lua
-- UNLOCK_SCRIPT
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0  -- value 不匹配，说明锁已被他人持有，不删除
end
```

### 使用场景

| 锁 Key | TTL | 用途 |
|--------|-----|------|
| `lock:Alipay:create:{orderNo}` | 60 秒 | 支付宝创建支付防并发 |
| `lock:payment:create:{orderNo}` | 60 秒 | Stripe 创建支付防并发 |

---

## Token 黑名单

**实现类：** `util/TokenBlacklistService.java`

### 黑名单 Key 设计

单个 Token 加入黑名单时，使用 SHA-256 前 16 位 hex（8 字节）作为 Key 后缀，节省内存：

```java
// hashToken() —— SHA-256 前 8 字节（16 位 hex）
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 8; i++) {
    sb.append(String.format("%02x", hashBytes[i]));
}
// 示例 Key: auth:blacklist:a1b2c3d4e5f6a7b8
```

**碰撞概率：** 2^64 空间，在系统正常 Token 规模下碰撞概率极低。

### 单 Token 黑名单

用户主动登出时，将 Token 加入黑名单，TTL = Token 剩余有效期：

```java
public void blacklist(String token, Date expiresAt) {
    long remainingMs = expiresAt.getTime() - System.currentTimeMillis();
    if (remainingMs <= 0) return; // 已过期，无需加入黑名单
    stringRedisTemplate.opsForValue().set(
        BLACKLIST_PREFIX + hashToken(token),
        "1",
        remainingMs,
        TimeUnit.MILLISECONDS
    );
}
```

### 用户强制登出

管理员禁用用户或修改角色时，写入强制登出标记（TTL=24h），标记值为当前时间戳：

```java
// auth:force-logout:{userId} = System.currentTimeMillis()
public void forceLogoutUser(Long userId) {
    stringRedisTemplate.opsForValue().set(
        FORCE_LOGOUT_PREFIX + userId,
        String.valueOf(System.currentTimeMillis()),
        24, TimeUnit.HOURS
    );
}

// 验证时：token.issuedAt < forceLogoutAt → 视为无效
public boolean isForceLoggedOut(Long userId, long tokenIssuedAt) {
    String value = stringRedisTemplate.opsForValue().get(FORCE_LOGOUT_PREFIX + userId);
    if (value == null) return false;
    long forceLogoutAt = Long.parseLong(value);
    return tokenIssuedAt < forceLogoutAt; // token 在强制登出之前签发，认为已失效
}
```

---

## 用户认证缓存

**实现类：** `util/UserAuthCacheService.java`

**Key 格式：** `auth:user:{userId}`，String（JSON），TTL 30 分钟

### 作用

在 `JwtAuthenticationFilter` 中替代每次请求查询数据库，提升认证性能。

缓存内容（`UserAuthCache` DTO）：

| 字段 | 说明 |
|------|------|
| `id` | 用户 ID |
| `username` | 用户名 |
| `email` | 邮箱 |
| `role` | 角色（USER/ADMIN）|
| `status` | 用户状态 |
| `createdAtMillis` | 用户创建时间（毫秒，用于 JWT 验证）|

**注意：** 缓存内容不含 `password` 字段，安全且轻量。

### 读取与写入

```java
// 读：缓存未命中或反序列化异常均返回 null，调用方降级查 DB
public UserAuthCache getUser(Long userId) {
    String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
    if (json == null) return null;
    try {
        return objectMapper.readValue(json, UserAuthCache.class);
    } catch (Exception e) {
        stringRedisTemplate.delete(KEY_PREFIX + userId); // 清除脏数据
        return null;
    }
}

// 写：用户信息变更时先 evict 再写入
public void putUser(UserDO user) {
    String json = objectMapper.writeValueAsString(buildCache(user));
    stringRedisTemplate.opsForValue().set(KEY_PREFIX + user.getId(), json, 30, TimeUnit.MINUTES);
}
```

---

## 接口限流

**实现类：** `aspect/RateLimiterAspect.java`

**限流注解：** `@RateLimiter(count=N, period=T)` 标注在 Controller 方法上

### Lua 固定窗口计数器

```lua
-- RATE_LIMIT_SCRIPT：原子 INCR + 首次设置 EXPIRE
local count = redis.call('incr', KEYS[1])
if count == 1 then
    redis.call('expire', KEYS[1], ARGV[1])  -- 首次请求时设置窗口期
end
return count  -- 返回当前计数
```

### Key 构成

```
ratelimit:{identity}:{keySuffix}
```

- **identity：**
  - 已认证用户：`user:{userId}`
  - 未认证：`ip:{clientIp}`
- **keySuffix：** `@RateLimiter(key="xxx")` 指定，默认为方法名

**示例：** `ratelimit:user:12345:createOrder`

### AOP 处理逻辑

```java
@Around("@annotation(rateLimiter)")
public Object around(ProceedingJoinPoint joinPoint, RateLimiter rateLimiter) throws Throwable {
    String key = buildKey(joinPoint, rateLimiter);
    Long count = stringRedisTemplate.execute(
        RATE_LIMIT_SCRIPT,
        Collections.singletonList(key),
        String.valueOf(rateLimiter.period()) // 窗口期（秒）
    );
    if (count != null && count > rateLimiter.count()) {
        throw new BusinessException(ResultCode.RATE_LIMIT_EXCEEDED);
    }
    return joinPoint.proceed();
}
```

---

## 降级策略

| 组件 | 降级策略 |
|------|---------|
| 商品缓存 | 反序列化失败删除脏缓存，回源查 DB |
| 库存缓存 | 缓存未加载返回 -1，懒加载后重试；Redis 不可用时降级为直接 DB 扣减 |
| 分布式锁 | 获取失败直接拒绝请求，不降级（防止超卖）|
| Token 黑名单 | Redis 异常时降级为**放行**（可用性优先），避免因 Redis 故障导致所有用户无法访问 |
| 用户认证缓存 | 反序列化失败/Redis 异常时回源查 DB |
| 接口限流 | Redis 异常时降级**放行**（可用性优先），避免限流故障影响正常业务 |

---

## Redis 配置说明

**配置类：** `config/RedisConfig.java`

```java
// 强制 Lettuce 使用 RESP2 协议
// 解决 Redis 8.x 下 HELLO 命令认证顺序问题
@Bean
public LettuceClientConfigurationBuilderCustomizer lettuceClientConfigurationBuilderCustomizer() {
    return builder -> builder.clientOptions(
        ClientOptions.builder()
            .protocolVersion(ProtocolVersion.RESP2)
            .build()
    );
}
```

**背景：** Redis 6+ 支持 RESP3 协议，Lettuce 默认尝试使用 `HELLO 3` 命令升级协议。在某些 Redis 8.x 版本中，`HELLO` 命令的认证顺序与期望不符，导致连接失败。强制使用 RESP2 可规避此问题。
