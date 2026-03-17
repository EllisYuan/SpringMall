# 系统架构

## 分层架构（请求链路全景）

展示一个请求从用户浏览器到数据库的完整链路，以及 Redis / RabbitMQ 在各层的交互位置。

```mermaid
graph TD
    Browser["🌐 用户浏览器"]
    Browser --> Nginx

    subgraph Docker["Docker Compose Network"]
        Nginx["Nginx<br/>静态资源 · /api/ 反向代理"]
        Nginx --> FilterChain

        subgraph SpringBoot["Spring Boot 应用"]
            FilterChain["Spring Security Filter Chain<br/>JwtAuthenticationFilter"]
            FilterChain -->|Token 验证| RedisAuth[("Redis<br/>认证缓存 · Token 黑名单")]
            FilterChain --> AOP

            subgraph AOP_Layer["AOP 切面层"]
                AOP["AccessLogAspect → RateLimiterAspect → BusinessLogAspect"]
            end
            AOP -->|限流计数| RedisRate[("Redis<br/>Lua 滑动窗口限流")]

            AOP --> Controller
            Controller["Controller<br/>@Valid DTO → Result＜VO＞"]
            Controller --> Service

            subgraph Service_Layer["Service 业务逻辑层"]
                Service["Service Impl"]
                Service -->|商品缓存 · 库存预扣 · 分布式锁| RedisCache[("Redis")]
                Service -->|订单超时延迟 · 掉单补偿延迟| RabbitMQ[("RabbitMQ<br/>DLQ 延迟模式")]
                Service -->|支付宝 · Stripe| Payment["第三方支付 API"]
            end

            Service --> Mapper
            Mapper["Mapper<br/>MyBatis XML"]
        end

        Mapper --> MySQL[("MySQL 8<br/>9 张表 · utf8mb4")]
    end

    style Browser fill:#E3F2FD,stroke:#1565C0,color:#000
    style Nginx fill:#FFF3E0,stroke:#E65100,color:#000
    style FilterChain fill:#FCE4EC,stroke:#C62828,color:#000
    style Controller fill:#E8F5E9,stroke:#2E7D32,color:#000
    style Service fill:#E8F5E9,stroke:#2E7D32,color:#000
    style Mapper fill:#E8F5E9,stroke:#2E7D32,color:#000
    style MySQL fill:#FFF9C4,stroke:#F57F17,color:#000
    style RedisAuth fill:#FFCDD2,stroke:#B71C1C,color:#000
    style RedisRate fill:#FFCDD2,stroke:#B71C1C,color:#000
    style RedisCache fill:#FFCDD2,stroke:#B71C1C,color:#000
    style RabbitMQ fill:#FFE0B2,stroke:#E65100,color:#000
    style Payment fill:#E1BEE7,stroke:#6A1B9A,color:#000
```

---

## 基础设施拓扑（Docker Compose）

展示 5 个容器的网络关系、端口映射、数据卷和启动依赖。

```mermaid
graph LR
    subgraph Host["宿主机"]
        P1[":26115"] -.-> FE
        P2[":25116"] -.-> BE
        P3[":3306"] -.-> DB
        P4[":6379"] -.-> RD
        P5[":5672"] -.-> MQ
        P6[":15672"] -.-> MQ
    end

    subgraph Network["mall-network (bridge)"]
        FE["frontend<br/>Nginx :80<br/>Vue 3 SPA"]
        BE["backend<br/>Spring Boot :8080"]
        DB[("mysql<br/>MySQL 8.0")]
        RD[("redis<br/>Redis 8.4")]
        MQ[("rabbitmq<br/>RabbitMQ 4.2")]

        FE -->|"/api/ 反向代理"| BE
        BE -->|"service_healthy"| DB
        BE -->|"service_healthy"| RD
        BE -->|"service_healthy"| MQ
    end

    subgraph Volumes["持久化数据卷"]
        V1["mysql-data"]
        V2["redis-data"]
        V3["rabbitmq-data"]
    end

    DB --- V1
    RD --- V2
    MQ --- V3

    style Host fill:#E3F2FD,stroke:#1565C0,color:#000
    style Network fill:#E8F5E9,stroke:#2E7D32,color:#000
    style Volumes fill:#FFF3E0,stroke:#E65100,color:#000
    style FE fill:#C8E6C9,stroke:#388E3C,color:#000
    style BE fill:#C8E6C9,stroke:#388E3C,color:#000
    style DB fill:#FFF9C4,stroke:#F57F17,color:#000
    style RD fill:#FFCDD2,stroke:#B71C1C,color:#000
    style MQ fill:#FFE0B2,stroke:#E65100,color:#000
```

---

## 核心业务流程（订单生命周期）

展示下单 → 库存预扣 → 支付 → 掉单补偿 → 超时关单 → 退款的完整链路，标注 Redis 和 RabbitMQ 在每个环节的作用。

```mermaid
flowchart TD
    Start(["用户下单"]) --> Lock1{"Redis 分布式锁<br/>防重复下单"}
    Lock1 -->|获取成功| LuaStock["Redis Lua 原子预扣库存"]
    Lock1 -->|获取失败| Reject1["返回：请勿重复提交"]
    LuaStock -->|库存充足| DBStock["DB 乐观锁扣减<br/>WHERE stock >= qty"]
    LuaStock -->|库存不足| Reject2["返回：库存不足"]
    DBStock --> CreateOrder["创建订单（UNPAID）"]
    CreateOrder --> MQ1["📮 RabbitMQ<br/>发送 15min 延迟消息"]

    CreateOrder --> Pay(["用户发起支付"])
    Pay --> Lock2{"Redis 分布式锁<br/>防重复支付"}
    Lock2 -->|获取成功| ClosePending["关闭其他 PENDING 支付"]
    Lock2 -->|获取失败| Reject3["返回：支付处理中"]
    ClosePending --> CallAPI["调用第三方支付<br/>支付宝 / Stripe"]
    CallAPI --> CreatePayment["创建支付记录（PENDING）"]
    CreatePayment --> MQ2["📮 RabbitMQ<br/>发送 5min 延迟消息"]

    MQ1 --> Consumer1["⏰ OrderCloseConsumer<br/>15min 后触发"]
    Consumer1 --> CheckUnpaid{"订单仍为<br/>UNPAID？"}
    CheckUnpaid -->|是| CancelOrder["取消订单 + 恢复库存"]
    CheckUnpaid -->|否| Ignore1["跳过（已支付/已取消）"]

    MQ2 --> Consumer2["⏰ PaymentCheckConsumer<br/>5min 后触发"]
    Consumer2 --> QueryThird["查询第三方实际支付状态"]
    QueryThird --> Reconcile{"对账结果"}
    Reconcile -->|支付成功| Confirm["确认订单 PAID"]
    Reconcile -->|未支付| Ignore2["等待下次检查"]
    Reconcile -->|多重支付| AutoRefund["自动退款"]

    style Start fill:#E3F2FD,stroke:#1565C0,color:#000
    style Pay fill:#E3F2FD,stroke:#1565C0,color:#000
    style Lock1 fill:#FFCDD2,stroke:#B71C1C,color:#000
    style Lock2 fill:#FFCDD2,stroke:#B71C1C,color:#000
    style LuaStock fill:#FFCDD2,stroke:#B71C1C,color:#000
    style MQ1 fill:#FFE0B2,stroke:#E65100,color:#000
    style MQ2 fill:#FFE0B2,stroke:#E65100,color:#000
    style Consumer1 fill:#FFE0B2,stroke:#E65100,color:#000
    style Consumer2 fill:#FFE0B2,stroke:#E65100,color:#000
    style CreateOrder fill:#C8E6C9,stroke:#388E3C,color:#000
    style DBStock fill:#FFF9C4,stroke:#F57F17,color:#000
    style CallAPI fill:#E1BEE7,stroke:#6A1B9A,color:#000
    style AutoRefund fill:#E1BEE7,stroke:#6A1B9A,color:#000
    style Reject1 fill:#EFEBE9,stroke:#795548,color:#000
    style Reject2 fill:#EFEBE9,stroke:#795548,color:#000
    style Reject3 fill:#EFEBE9,stroke:#795548,color:#000
```

**图例**：🔴 Redis 相关 · 🟠 RabbitMQ 相关 · 🟢 业务主流程 · 🟣 第三方支付 · 🟡 数据库操作
