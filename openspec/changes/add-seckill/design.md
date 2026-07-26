## Context

项目已有成熟的高并发下单地基,秒杀模块在其之上叠加"抢购准入 + 异步削峰",不另起炉灶。可直接复用的既有能力:

- **库存双重保障**:`StockRedisService`(Lua 批量预扣)+ DB 乐观锁扣减,常规下单已在用。
- **RabbitMQ 死信延迟队列**:`order.delay.queue` → DLX → `order.close.queue` → `OrderCloseConsumer`,15 分钟超时关单并回补库存。
- **支付闭环**:支付宝/Stripe + 5 分钟掉单补偿(`PaymentCheckConsumer`)。
- **并发范式**:`RedisDistributedLock`、`TransactionSynchronization.afterCommit()` 发消息、编程式事务 + 手动 ACK、`compareAndUpdateStatus` 乐观锁。
- **人机验证**:`TurnstileService`(认证增强已引入)。
- **接口限流**:`@RateLimiter`。

工作树中存在一套旧秒杀实现的残骸(半拆除、无法编译)与备份 tag `backup/seckill-3388fbf`(完整可编译),旧实现采用独立 `mall_seckill_order` 表(注:与本设计新表同名但结构/定位不同,详见 D1)+ SK 前缀路由。本设计基于讨论结论,以复用真实订单表的方式重做,残骸仅作参考;**新实现验收通过后须删除该残骸文件**(半拆除、无法编译的旧秒杀源码/视图),避免遗留死代码干扰后续维护,备份 tag 作为历史存档保留不删除。

约束:遵守 `.claude/rules`(Controller 返回 VO、DO 不越界、MapStruct 转换、SQL 全在 XML、错误码在 `ResultCode`)、安全红线(不碰 `.env`、schema 变更须有回滚、仅连开发库)。

## Goals / Non-Goals

**Goals:**
- 抢购主链路的瞬时洪峰只触达 Redis,DB 落单经 MQ 削峰后串行完成。
- 严格防超卖:Redis 分桶原子预扣(前置)+ DB 活动行写锁 + 乐观锁扣减(兜底)双层保障。
- 中签订单为真实 `mall_order`,完全复用支付/退款/我的订单/后台订单/超时关单。
- 秒杀库存与商品库存账面一致:上线预留、下线/结束归还,杜绝物理超卖。
- 全防线可展示:限流、风控+Turnstile、总量闸门、售罄快失败、缓存预热、幂等。
- 抢购结果通过 SSE 实时推送给用户,断线可降级为一次性查询兜底。

**Non-Goals:**
- 不与购物车合并结算;秒杀为单商品单数量直接抢购。
- 不为秒杀新增独立支付/退款规则。
- 不做多机 Redis 集群分片(单实例/主从即可,分桶用于缓解单 key 热点而非分片)。
- 不引入双工 WebSocket;推送用单向 SSE 即可满足"结果通知"场景。

## Decisions

### D1. `mall_order` 承载真实订单 + `mall_seckill_order` 承载秒杀明细快照(双表)
中签落一条真实 `mall_order`(`source`=秒杀,`seckill_activity_id` 记录来源)+ 单条 `mall_order_item`;同一事务内额外写入独立的 `mall_seckill_order` 表(与 `mall_order` 通过 `order_id` 1:1 关联),记录活动 ID、秒杀价、商品名/规格/主图快照、`request_id`(唯一索引,幂等用)。
- **理由**:`mall_order` 保持真实订单语义,支付、退款、掉单补偿、我的订单、后台订单全部零改动复用;`mall_seckill_order` 专门承载秒杀维度的详情快照与幂等/限购统计,"我的秒杀订单"、后台秒杀报表等秒杀专属查询直接读这张表,不用现拼 `mall_order` + 活动表。
- **备选 A(旧设计)**:完全独立的 `mall_seckill_order` 表承载购买记录本身、替代 `mall_order`,配 SK 前缀路由——隔离彻底,但支付/退款/关单/列表处处要加前缀分支,分支扩散、维护成本高。已否决。
- **备选 B**:只建纯幂等表(不带快照字段),秒杀专属查询靠 JOIN `mall_order`+`mall_seckill_activity`——省存储,但查询复杂、后续加审计字段要改查询而非加列。已否决。
- **代价**:超时关单的库存回补需按 `source` 分流(见 D9),这是唯一接缝;`mall_order`/`mall_order_item`/`mall_seckill_order` 三写需保持同一事务原子性。
- 订单/订单项按"多数据源"允许手动构建(符合 backend-java 规约对 `OrderDO`/`OrderItemDO` 的例外)。

### D2. 独立秒杀库存池 + 真正预留
`mall_seckill_activity.available_stock` 为秒杀权威库存;活动**上线时**从 `mall_product.stock` 划走 `seckill_stock`(同事务扣商品 DB 库存 + 同步扣商品 Redis 库存),**下线/删除/结束**归还未售部分。
- **理由**:防止"商品库存 + 秒杀库存"双账并行售卖导致物理超卖。
- **备选**:旧设计仅在创建时校验 `seckillStock ≤ product.stock`、不真正预留——实现简单但账面双算。已否决。
- **一致性四处**:预留(上线)、扣减(消费者)、回补(超时/失败)、归还(下线/结束)必须对得上账,列为审查重点。

### D3. Redis 分桶 Lua 原子预扣抗热点
单活动库存拆为 N 桶(`seckill:stock:{activityId}:{bucket}`),抢购随机起桶做 Lua 原子扣;桶空(-2)跨桶顺序重试,未预热(-1)懒加载后重试一次,全桶空判真实售罄置售罄标记,超限购(-3)全局键控制不换桶。
- **理由**:缓解单 key 热点,提升并发扣减吞吐,是核心技术亮点。
- **备选**:单 key 预扣——实现简单但热点集中。保留为可配(桶数=1 即退化为单 key)。

### D4. MQ 异步削峰,请求线程不落库
Redis 预扣成功后写 `result=QUEUING`、投递 `seckill.order.queue`、立即返回排队中;`SeckillOrderConsumer` 消费后串行落库,结果经 SSE 推送给前端(见 D10)。
- **理由**:瞬时洪峰与 DB 写解耦,削峰填谷。
- **备选**:同步落库——高并发下 DB 连接与锁竞争成为瓶颈。已否决。

### D5. 幂等双保险
消费入口 `SETNX`(`seckill:msg:done:{requestId}`)原子抢占;DB 侧 `mall_seckill_order.request_id` 唯一索引兜底;`DuplicateKeyException` 视为重复消费安全跳过。限购 SUM 查 `mall_seckill_order`,与订单主表解耦。
- **理由**:MQ 至少一次投递语义下必须幂等;Redis 幂等位可能过期,故加 DB 兜底。

### D6. DB 二道防线:活动行写锁 + 锁内限购 + 乐观锁扣减
消费者事务内 `SELECT ... FOR UPDATE` 活动行 → 锁内 SUM 限购校验 → 乐观锁 `UPDATE available_stock = available_stock - qty WHERE available_stock >= qty`。
- **理由**:串行化同一活动的并发消费者,杜绝"并发消息同时通过限购校验后超购",并兜底 Redis 与 DB 的漂移。

### D7. 风控 + Turnstile step-up,未触发零开销
每次抢购入口记录 user/IP 频率计数(与判断分离);频率/失败计数超阈值判高风险,高风险且无令牌抛 `SECKILL_CAPTCHA_REQUIRED`,携带令牌走 `TurnstileService.verify` 通过后清零风险计数。风控在扣库存之前完成。
- **理由**:复用既有 Turnstile,平时零额外开销,仅对可疑流量加验证。

### D8. 总量闸门 + 售罄标记
闸门 `INCR seckill:gate:{activityId}` 上限 `seckill_stock × k`,超限快速拒绝防过载;全桶空时置 `seckill:soldout:{activityId}` 快速失败。
- **理由**:在真正扣库存前挡掉绝大部分注定失败的请求。

### D9. 超时关单按 `source` 分流回补,秒杀单再按活动"是否允许二次流出"二次分流(唯一接缝)
`OrderCloseConsumer` 增加分支:`source`=普通 → 原商品库存逻辑;`source`=秒杀 → 读取活动配置 `allow_restock`(是否允许二次流出)再分流:
- **允许(allow_restock=1,默认)**:回补 `available_stock` + 秒杀 Redis 分桶,**同时清除售罄标记、按回补量回退闸门额度**——不清标记/不退闸门,回流库存会被售罄快失败与闸门防线挡住,永远无人能抢到。库存重新公开放出,先到先得(业界常规)。
- **不允许(allow_restock=0)**:仅回补 `available_stock` 账面(DB 权威),不回 Redis 分桶、售罄标记与闸门保持不变,活动期内该库存不再放出;活动下线/结束时随既有归还流程回商品库存,账面守恒不变。
回补与关单同事务、幂等(仅处理 UNPAID 且无 PENDING 支付)。
- **理由**:按 source 分流是 D1 复用订单表的必然代价,改动小且集中于一处。`allow_restock` 开关回应公平性问题——先失败的乙眼看着更晚参与的丙捡漏买到超时释放的库存,"晚来的反而成功";管理员可按活动关闭二次流出,彻底杜绝捡漏,代价是未付部分流拍、秒杀成交量可能低于 `seckill_stock`。
- **备选**:候补队列 FIFO(12306 式,释放库存定向给最早失败者)——公平最彻底,但需新增候补接口/存储/流转链路,复杂度高。本次不做,留作后续演进方向。

### D10. 结果通过 SSE 推送,Redis 键兜底断线重连
`seckill:result:{activityId}:{userId}` 仍存 `QUEUING`/`SUCCESS:{orderNo}`/`FAIL:{reason}` 作为权威状态;抢购返回排队中后,前端立即通过 `GET /api/v1/seckill/{activityId}/stream` 建立 SSE 连接,`SeckillSseService` 按 `userId`(+`activityId`)注册 `SseEmitter`。`SeckillOrderConsumer` 落库成功/失败后,除写 Redis 结果键外,同时向该用户的 `SseEmitter`(若在线)推送一条事件;SUCCESS 跳支付页,FAIL 提示原因,CAPTCHA_REQUIRED 弹 Turnstile。前端若未建立 SSE 或连接断开,可退化为一次性调用既有 `getSeckillResult` 接口查询兜底(不做持续轮询)。
- **理由**:相比持续轮询,SSE 免去无效请求与轮询间隔带来的体感延迟;相比 WebSocket,SSE 单向推送已够用、实现与连接管理更轻量(基于 Spring `SseEmitter`,复用现有认证)。
- **备选**:纯前端轮询——实现简单但请求量大、体感延迟高。已否决(本次改为 SSE)。
- **代价/风险**:`SseEmitter` 存活在建立连接的应用实例内存中;若消费者处理消息的实例与用户 SSE 连接所在实例不是同一台,需经 Redis Pub/Sub 广播结果事件、由持有对应连接的实例转发。当前项目未做多机分片(见 Non-Goals),单实例部署下可直接内存转发;多实例水平扩展时需补充 Pub/Sub 桥接,列入 Risks。

### D11. 预热合并进"上线"动作,不新增定时任务
预热(`available_stock` 灌入 Redis 分桶 + 初始化闸门)不再由独立定时任务承担,而是作为"上线"(`activateActivity`)的同步收尾步骤——DB 预留库存提交后紧接着完成 Redis 预热,一次操作两件事一起做完。叠加两道兜底:①管理端保留手动"重新预热"入口(`preheatActivity`,覆盖式写),供已上线活动在开抢前需要刷新库存快照时使用;②沿用 D3 既有的懒加载兜底(桶未预热时首次抢购懒加载一次)。
- **理由**:活动本就以人工"上线"驱动生效(见 D2/迁移计划:草稿态默认不生效,上线才生效),预热没有独立于上线之外的触发时机需求;省去一个定时任务的注册、调度、监控与故障处理成本。
- **备选**:`SeckillPreheatJob`(XXL-Job)定时扫描"即将开始"的活动做预热——与"上线"语义重复(两次触发同一件事),徒增一个需要单独排障的调度组件。已否决。
- **代价**:活动上线后、开抢前如需调整库存,需走管理端手动重新预热覆盖写,或先下线再重新上线;不提供"上线中静默改库存"的能力。

## 主链路与库存生命周期

```
抢购 POST /api/v1/seckill/{activityId}
 ①限流 ②活动/时间校验 ③频率计数→风控→(高风险)Turnstile
 ④总量闸门 ⑤售罄标记 ⑥写QUEUING ⑦分桶Lua原子预扣 ⑧发MQ+requestId→返回排队中
 (⑦失败置售罄/超限购; ⑧失败回补Redis+写FAIL)
        │ seckill.order.queue
        ▼
SeckillOrderConsumer:
 SETNX抢占 → 事务{ FOR UPDATE活动行 → 锁内限购SUM → 乐观锁扣available_stock
   → 地址归属校验 → 插mall_order(UNPAID,source=秒杀)+order_item + 插mall_seckill_order(快照+requestId) }
 → afterCommit: sendOrderCloseDelayMessage(复用15min) + 写SUCCESS:{orderNo} + SSE推送
 → DuplicateKey/业务失败: 回补Redis + 写FAIL + SSE推送

库存生命周期(账面守恒):
 上线: product.stock -= seckill_stock (+商品Redis同步) → available_stock=seckill_stock,同步预热 Redis 分桶 + 初始化闸门(同一动作完成,无需定时任务;管理端另提供手动重新预热兜底)
 抢购: Redis 分桶预扣 → 消费者 DB 扣 available_stock
 超时未付: 按活动 allow_restock 分流
   ├─ 允许二次流出: 回补 available_stock + 秒杀Redis分桶 + 清售罄标记 + 回退闸门额度
   └─ 不允许: 仅回补 available_stock 账面(不回分桶,活动期内不再放出)
 下线/删除/结束: product.stock += 未售 available_stock (+商品Redis同步), 清活动Redis键
```

## Risks / Trade-offs

- **库存四处一致性对不上账**(预留/扣减/回补/归还)→ 归还操作加"已回收"幂等标记;DB `available_stock` 为权威,乐观锁保证非负;code-reviewer 重点核对四处闭环。
- **Redis 与 DB 库存漂移**(网络抖动、回补丢失)→ DB 为权威兜底,可选加定时对账;失败路径一律回补 Redis。
- **活动结束归还依赖定时任务,Job 故障致库存滞留** → 下线/删除时也会归还,并支持管理员手动触发;归还幂等可重跑。
- **预留改动商品库存与常规下单并发** → 上线预留在同事务内乐观锁扣商品库存,库存不足则整体回滚,不产生负库存。
- **MQ 重复/丢失** → SETNX + DB 唯一索引幂等;投递失败与消费异常均回补 Redis;NACK 不重入队防死循环。
- **分桶"某桶空但总量有余"** → 跨桶重试;售罄标记仅在全桶空时置,避免误判售罄。
- **SSE 连接跨实例不可达**(消费者所在实例与用户连接所在实例不同)→ 单实例部署下无影响;后续水平扩展需引入 Redis Pub/Sub 广播结果事件;过渡期前端保留断线降级查询兜底,不影响最终一致性。
- **关闭二次流出导致成交量低于 `seckill_stock`**(超时未付部分流拍)→ 管理员按活动主动选择的公平性取舍;流拍库存在活动结束/下线时随归还流程回商品库存走常规销售,不损失账面。

## Migration Plan

1. **DB**:新建 `mall_seckill_activity`、`mall_seckill_order`(秒杀订单明细快照 + 幂等/限购);`mall_order` 增 `source`、`seckill_activity_id` 两列(带默认值,兼容存量数据)。提供并对齐 `db/teardown_seckill.sql` 回滚脚本;执行前先 `mysqldump` 备份相关表。仅在本机开发库 `mall` 执行。
2. **RabbitMQ**:声明 `seckill.order.exchange/queue`(幂等声明,不影响现有队列);超时关单复用现有 `order.delay` 链路,不新增延迟队列。
3. **配置**:`SeckillProperties` 新增分桶数、闸门倍数、限流/风控阈值、各 TTL,均给安全默认值写入 `application.yml`。
4. **灰度**:活动默认 status=0 草稿态,上线才生效;先用小库存活动全链路验证(抢购→中签→支付→超时回补→归还)。
5. **回滚**:下线所有活动归还库存 → 停用秒杀队列消费 → 备份后执行 `teardown_seckill.sql` → 撤销 `mall_order` 两列(如需)。

## Open Questions

- 分桶数默认值(建议 10;可按活动库存量级配置)。
- 闸门倍数 k 默认值(建议 5~10)。
- 限流与风控阈值(频率窗口/次数、失败阈值)的默认值取值。
- 活动结束归还 Job 的调度频率(建议与 XXL-Job 现有节奏对齐,如每分钟扫描)。
- `SseEmitter` 超时时长与心跳间隔的默认值(建议超时略长于 MQ 消费预期耗时,心跳防代理层空闲断连)。
