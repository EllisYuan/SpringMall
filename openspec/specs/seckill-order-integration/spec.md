# seckill-order-integration

## Purpose

中签后落真实 `mall_order`(并写 `mall_seckill_order` 明细快照)、复用现有支付与退款、消费者幂等与 DB 二道防线防超卖,以及超时未付按订单来源与活动 `allow_restock` 配置回补秒杀库存的整合契约。

## Requirements

### Requirement: 中签落真实订单
系统 SHALL 在 MQ 消费者中为中签请求生成一条真实 `mall_order`(状态 UNPAID,`source` 标记为秒杀,记录 `seckill_activity_id`)及单条 `mall_order_item`,金额取秒杀价快照。订单落库 MUST 与现有下单共用同一订单表与订单号体系,以复用支付、退款、我的订单、后台订单等既有能力。同一事务内 MUST 额外写入 `mall_seckill_order`(与 `mall_order` 按 `order_id` 1:1 关联),记录活动 ID、秒杀价、商品名/规格/主图快照,供"我的秒杀订单"等秒杀专属查询使用。

#### Scenario: 消费成功生成订单
- **WHEN** 消费者处理一条合法的中签消息
- **THEN** 系统写入一条 UNPAID 且 source=秒杀 的 `mall_order` 与单条 order_item(金额为 `seckillPrice × quantity`)及一条关联的 `mall_seckill_order` 明细快照,并将用户抢购结果置为 SUCCESS 携带订单号

#### Scenario: 收货地址归属校验
- **WHEN** 消息携带的 `addressId` 不存在或不属于该用户
- **THEN** 系统不生成订单,回补 Redis 库存,结果置为失败

### Requirement: 消费者幂等
系统 SHALL 保证同一抢购请求即使消息重复投递也只落一单:消费入口以 `requestId` 做原子 SETNX 抢占,并在 `mall_seckill_order` 上对 `request_id` 建唯一索引作为 DB 兜底;重复消费 MUST 安全跳过且不重复回补库存。

#### Scenario: 重复消息被 SETNX 拦截
- **WHEN** 同一 `requestId` 的消息被重复投递
- **THEN** 后到消息在 SETNX 抢占失败后直接 ACK 跳过,不重复落单

#### Scenario: DB 唯一索引兜底
- **WHEN** SETNX 幂等位已失效但记录已存在,插入 `mall_seckill_order` 触发唯一索引冲突
- **THEN** 系统视为重复消费安全跳过,不回补库存

### Requirement: DB 二道防线防超卖
系统 SHALL 在消费者落单事务内对活动行加写锁(SELECT FOR UPDATE),持锁后先做 DB 权威限购 SUM 校验,再以乐观锁扣减 `available_stock`;扣减失败(库存不足)时事务回滚并回补 Redis。

#### Scenario: 持锁限购校验拦截超购
- **WHEN** 持有活动写锁后,用户在 `mall_seckill_order` 的累计购买量加本次超过 `limit_per_user`
- **THEN** 事务回滚,结果置为失败(超过限购),回补 Redis

#### Scenario: 乐观锁扣减兜底
- **WHEN** DB 乐观锁扣减 `available_stock` 影响行数为 0(库存不足)
- **THEN** 事务回滚,结果置为失败(售罄),回补 Redis

### Requirement: 事务提交后发消息与手动 ACK
系统 SHALL 在消费者中使用编程式事务,数据库事务提交成功后才发送超时关单延迟消息并写成功结果,再手动 ACK;业务异常时 NACK 且不重新入队,防止死循环消费。

#### Scenario: 提交后发关单延迟消息
- **WHEN** 订单落库事务成功提交
- **THEN** 系统复用现有 15 分钟超时关单延迟链路发送该订单的关单消息,并写结果 SUCCESS

#### Scenario: 异常不重入队
- **WHEN** 消费过程中抛出异常
- **THEN** 系统回补 Redis、写失败结果,并以 requeue=false 拒绝消息

### Requirement: 中签订单复用支付与退款
系统 SHALL 使秒杀订单走与常规订单完全一致的支付(支付宝/Stripe)与退款流程,不为秒杀订单新增支付或退款分支。

#### Scenario: 中签后支付
- **WHEN** 用户对 UNPAID 的秒杀订单发起支付
- **THEN** 系统按现有支付链路创建支付并在回调后将订单置为 PAID

### Requirement: 超时未付按来源回补库存
系统 SHALL 使超时关单消费者按订单 `source` 分流回补库存:常规订单仍回补商品库存;秒杀订单超时未付时一律回补 `available_stock` 账面,并按活动 `allow_restock`(是否允许二次流出)配置决定是否重新放出——允许时 MUST 同步回补秒杀 Redis 分桶、清除售罄标记并回退闸门额度(使回流库存重新可抢);不允许时 MUST NOT 回补 Redis 分桶,售罄标记与闸门保持不变,该库存活动期内不再放出,待活动下线/结束随归还流程回商品库存。回补 MUST 与关单在同一事务并保持幂等。

#### Scenario: 允许二次流出的秒杀订单超时回补
- **WHEN** 一条 source=秒杀 的 UNPAID 订单超时触发关单且无 PENDING 支付,且其活动 `allow_restock`=允许
- **THEN** 系统将其数量回补到对应活动的 `available_stock` 与秒杀 Redis 分桶,清除售罄标记并回退闸门额度,订单置为 CANCELLED,后续用户可再抢到该库存

#### Scenario: 不允许二次流出的秒杀订单超时回补
- **WHEN** 一条 source=秒杀 的 UNPAID 订单超时触发关单且无 PENDING 支付,且其活动 `allow_restock`=不允许
- **THEN** 系统仅将其数量回补到 `available_stock` 账面,不回补 Redis 分桶、不清售罄标记,订单置为 CANCELLED,活动期内该库存不再可抢

#### Scenario: 常规订单回补商品库存不受影响
- **WHEN** 一条 source=普通 的订单超时关单
- **THEN** 系统按原逻辑回补商品库存,不触碰秒杀库存池

#### Scenario: 已支付订单跳过回补
- **WHEN** 关单消息到达时订单已非 UNPAID
- **THEN** 系统幂等跳过,不回补任何库存
