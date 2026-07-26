## Why

电商系统当前只有常规下单链路,缺少能承受瞬时高并发的秒杀/抢购能力。秒杀场景的特征是"短时间、超高并发、库存有限",直接打到常规下单链路会击穿数据库、并发扣减易超卖。本变更在**已有的高并发下单地基**(Redis Lua 预扣 + DB 乐观锁 + RabbitMQ 死信延迟队列 + 支付闭环)之上,叠加一层"抢购准入 + 异步削峰",既补齐业务能力,也作为项目高并发工程能力的完整展示。

## What Changes

- 新增**秒杀活动管理**:后台创建/编辑/上线/下线活动,定义秒杀价、总库存、每人限购、起止时间、**是否允许二次流出**(超时释放库存能否重新放出,默认允许);活动上线时从商品库存**真正预留** `seckill_stock`(联动扣减商品 DB/Redis 库存),下线/删除/结束时归还未售库存,避免与常规销售双重售卖导致物理超卖。
- 新增**用户端抢购链路**(全防线):接口限流 → 活动状态/时间校验 → 频率计数+风控判定+高风险 step-up Turnstile 人机验证 → 总量闸门快速拒绝 → 售罄标记快速失败 → Redis 分桶 Lua 原子预扣(随机起桶 + 跨桶重试)→ MQ 异步落单 → 立即返回"排队中"+ SSE 推送最终结果(断线降级为一次性查询兜底)。
- 新增**中签订单整合**:消费者削峰后串行落库,生成**真实 `mall_order`(UNPAID, source=秒杀)+ 单条 order_item**,复用现有支付(支付宝/Stripe)、退款、"我的订单"、后台订单——**零改动**;同一事务额外写入独立的 `mall_seckill_order` 表(与 `mall_order` 1:1 关联,存活动/秒杀价/商品快照,供"我的秒杀订单"专属查询);仅 `OrderCloseConsumer` 按订单来源分流,超时未付时回补秒杀库存池(而非商品库存),并按活动"是否允许二次流出"配置决定回补后是否重新放出(允许→回 Redis 分桶+清售罄标记;不允许→仅账面回补,活动期内不再放出,杜绝"先失败者眼看后来者捡漏"的不公平)。
- 新增**缓存预热**:预热与"上线"合并为同一动作——活动上线(预留库存提交后)同步完成 `available_stock` 灌入 Redis 分桶 + 初始化闸门,不新增独立定时任务;管理端保留手动"重新预热"入口,叠加既有懒加载兜底(未预热时首次抢购自动填充一次),避免开抢瞬间冷启动击穿 DB。
- 新增**幂等兜底**:`mall_seckill_order` 表对 `request_id` 建唯一索引,消费者 SETNX 抢占 + DB 唯一索引双重去重;限购 SUM 校验查该表,与订单主表解耦。
- 复用现有 `ResultCode.SECKILL_*`(40801–40811)错误码,不新增码段。

## Capabilities

### New Capabilities
- `seckill-activity`: 秒杀活动的后台全生命周期管理(CRUD、上线/下线、上线自动预热 + 手动重新预热),以及秒杀库存池与商品库存之间的预留/归还一致性。
- `seckill-purchase`: 用户端抢购主链路的准入与防护(限流、风控+人机验证、总量闸门、售罄快失败、Redis 分桶原子预扣、限购、SSE 结果推送)与异步削峰。
- `seckill-order-integration`: 中签后落真实订单、复用支付与退款、以及超时未付按订单来源回补秒杀库存的整合契约。

### Modified Capabilities
<!-- 无既有 spec 级能力被修改;OrderClose 的按来源分流回补作为 seckill-order-integration 的一部分定义。 -->

## Impact

- **数据库(需回滚方案)**:
  - 新建表 `mall_seckill_activity`(活动/库存权威,含 `allow_restock` 二次流出开关)、`mall_seckill_order`(秒杀订单明细快照 + 幂等/限购统计,与 `mall_order` 1:1 关联)。
  - `mall_order` 新增两列:`source`(0 普通 / 1 秒杀)、`seckill_activity_id`(可空)。
  - 配套 `db/teardown_seckill.sql` 回滚脚本(建表/改表的逆操作,已存在需对齐)。
- **后端新增**:`SeckillController`(用户端,含 SSE 结果流接口)、`admin/AdminSeckillController`、`SeckillService(+Impl)`、`SeckillActivityMapper`/`SeckillOrderMapper`(+XML)、`SeckillActivityDO`/`SeckillOrderDO`(秒杀订单明细快照)、`SeckillActivityDTO`/`SeckillOrderDTO`、`SeckillActivityVO`/`SeckillResultVO`、`SeckillConverter`、`SeckillMessageProducer`/`SeckillOrderConsumer`、`SeckillRedisService`(Lua 分桶)、`SeckillSseService`(SSE 连接注册与结果推送)、`SeckillActivityCloseJob`(结束归还扫描,XXL-Job)、`SeckillProperties`;`RabbitMQConfig` 新增 `seckill.order` 队列。
- **后端改动(小而集中)**:`OrderCloseConsumer` 增加按 `source` 分流的库存回补逻辑;活动上线/下线联动 `mall_product` 与 `StockRedisService` 商品库存。
- **前端**:`views/user/SeckillList`、`SeckillDetail`、`views/admin/SeckillManage`、`store/seckill.js`、`api/seckill.js`、倒计时与 Turnstile 交互组件、SSE 结果监听逻辑(复用工作树中已有残骸视图/组件,验收通过后连同旧秒杀残骸一并删除)。
- **复用不改**:支付(支付宝/Stripe)、退款、掉单补偿、15 分钟超时关单延迟链路、`TurnstileService`、`@RateLimiter`、`StockRedisService`。
