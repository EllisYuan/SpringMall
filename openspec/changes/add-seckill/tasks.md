## 1. 数据库与配置（backend-dev，合约源头，先行）

- [x] 1.1 编写建表脚本:`mall_seckill_activity`(活动/库存权威,含 `allow_restock` TINYINT(1) 默认 1 二次流出开关)、`mall_seckill_order`(秒杀订单明细快照:`order_id` 关联 `mall_order`、活动ID/秒杀价/商品名/规格/主图快照、幂等+限购;`request_id` 唯一索引),字段与类型对照 `.claude/rules`(ID=Long、金额=BigDecimal、时间=LocalDateTime)
- [x] 1.2 `mall_order` 增列 `source`(TINYINT, 默认 0)、`seckill_activity_id`(BIGINT, 可空),带默认值兼容存量数据
- [x] 1.3 对齐并更新 `db/teardown_seckill.sql`,使其为 1.1/1.2 建表/改表的完整逆操作(含 `mall_order` 撤列),脚本头部写明先 `mysqldump` 备份
- [x] 1.4 在开发库 `mall` 执行建表/改表(经 MySQL MCP inspect 确认列名/类型),不触碰测试/生产库
- [x] 1.5 `SeckillProperties`:分桶数、闸门倍数 k、限流阈值、风控频率/失败阈值与窗口、各 TTL、消息去重 TTL、售罄 TTL,均给安全默认值并写入 `application.yml`
- [x] 1.6 `RabbitMQConfig` 新增 `seckill.order.exchange/queue`(幂等声明,不影响现有队列);超时关单复用现有 `order.delay` 链路,不新增延迟队列

## 2. 后端数据层与领域模型（backend-dev）

- [x] 2.1 实体:`SeckillActivityDO`(含非持久化的商品名/主图/规格展示字段)、`SeckillOrderDO`(秒杀订单明细快照:order_id、活动ID、秒杀价、商品名/规格/主图快照、requestId)
- [x] 2.2 DTO:`SeckillActivityDTO`(Jakarta 校验,含 `allowRestock`)、`SeckillOrderDTO`(activityId/quantity/addressId/cfToken/requestId/userId)
- [x] 2.3 VO:`SeckillActivityVO`(含实时状态与 `allowRestock`)、`SeckillResultVO`(queuing/success/fail/notFound 工厂方法)
- [x] 2.4 `SeckillConverter`(MapStruct):`toDO`/`updateDOFromDTO`/`toVO`/`toVOList`/`toVOWithStatus`,忽略 id/时间/由 Service 决定的字段
- [x] 2.5 Mapper + XML:`SeckillActivityMapper`(findById、findActiveAndUpcoming、findAllForAdmin、insert、updateById、offlineById、selectForUpdate、decreaseStock 乐观锁、increaseStock 归还、结束归还扫描/标记)、`SeckillOrderMapper`(insert、sumQuantityByActivityAndUser、findByUserId 供"我的秒杀订单"列表)。SQL 全部写在 XML

## 3. 后端 Redis 与 MQ 基础设施（backend-dev）

- [x] 3.1 `SeckillRedisService`:分桶库存 Lua 原子预扣(随机起桶/跨桶重试/懒加载/超限购)、`setStock`/`preloadStock`/`removeStock`/`restoreStock`、限购计数、`setResult`/`getResult`、总量闸门 `incrementGate`/`initGate`、售罄标记 `markSoldOut`/`isSoldOut`、风控频率/失败计数与清零、`tryClaimMessage`(SETNX 幂等)
- [x] 3.2 `SeckillMessageProducer`:向 `seckill.order.queue` 投递 `SeckillOrderDTO`,含序列化配置对齐(避免反序列化拦截问题)

## 4. 后端活动管理与库存预留（backend-dev）

- [x] 4.1 `SeckillService` 接口 + `SeckillServiceImpl` 管理端:`adminListActivities`、`createActivity`(校验价/时间/库存/商品)、`updateActivity`、上线(status 0→1 且同事务从商品库存**预留** `seckill_stock`,联动 `mall_product` 与商品 Redis 库存,不足回滚;DB 提交后同步调用预热完成 Redis 分桶灌入 + 闸门初始化,无需定时任务)、下线/删除(归还未售 `available_stock` + 清活动 Redis 键)、`preheatActivity`(覆盖写分桶 + 初始化闸门;供上线内部调用与管理端手动重新预热复用)
- [x] 4.2 `AdminSeckillController`(`/api/v1/admin/seckill/**`,`@PreAuthorize("hasRole('ADMIN')")`),返回 `Result<VO>`,业务错误抛 `BusinessException`
- [x] 4.3 用户端查询:`listActiveActivities`、`getActivityDetail`(含实时状态)

## 5. 后端抢购主链路（backend-dev）

- [x] 5.1 `SeckillServiceImpl.seckill(dto, clientIp)`:①活动/时间校验 ②频率计数 ③风控判定+高风险 Turnstile step-up(无令牌抛 `SECKILL_CAPTCHA_REQUIRED`) ④总量闸门 ⑤售罄标记 ⑥写 QUEUING ⑦分桶原子预扣(处理 -1/-2/-3) ⑧生成 requestId+投递 MQ,立即返回排队中;⑧投递失败回补 Redis+写 FAIL
- [x] 5.2 `getSeckillResult(activityId, userId)`:解析 QUEUING/SUCCESS/FAIL/notFound,供 SSE 断线降级一次性查询兜底
- [x] 5.3 `SeckillController`(`/api/v1/seckill/**`,`@PreAuthorize("hasRole('USER')")`),抢购接口加 `@RateLimiter`(user+IP),`@CurrentUserId` 注入,`clientIp` 提取
- [x] 5.4 `SeckillSseService` + `SeckillController` 新增 `GET /api/v1/seckill/{activityId}/stream`(返回 `SseEmitter`,鉴权同抢购接口):按 `userId`(+`activityId`)注册/移除 emitter、超时自动关闭、推送 QUEUING 之后的最终结果事件

## 6. 后端中签消费与订单整合（backend-dev）

- [x] 6.1 `SeckillOrderConsumer`:SETNX 抢占幂等 → 编程式事务{ `selectForUpdate` 活动行 → 锁内限购 SUM 校验 → 乐观锁扣 `available_stock` → 地址归属校验 → 生成真实 `mall_order`(UNPAID, source=秒杀, seckill_activity_id)+ 单条 `order_item` → 插 `mall_seckill_order`(order_id关联+快照+requestId) } → afterCommit 复用 `sendOrderCloseDelayMessage` + 写 SUCCESS + `SeckillSseService` 推送结果;`DuplicateKeyException`/业务失败回补 Redis + 写 FAIL + SSE 推送;异常 NACK 不重入队
- [x] 6.2 `OrderCloseConsumer` 补丁:按订单 `source` + 活动 `allow_restock` 双重分流回补——普通单保持原商品库存逻辑;秒杀单一律回补 `available_stock`,再按配置分流:允许二次流出则同步回补秒杀 Redis 分桶 + 清售罄标记 + 回退闸门额度,不允许则仅账面回补(不回分桶,活动期内不再放出);同事务、幂等(仅 UNPAID 且无 PENDING 支付)
- [x] 6.3 验证秒杀订单走通现有支付(支付宝/Stripe)与退款链路,无需新增分支(代码层确认:支付/退款均按 orderNo 操作 `mall_order`,无 source 分支,秒杀单天然复用;运行时验收由 acceptance-validator 负责)

## 7. 后端结束归还（backend-dev）

- [x] 7.1 `SeckillActivityCloseJob`(XXL-Job):定时扫描已过 `endTime` 且未回收且 `available_stock`>0 的活动,归还商品库存并置"已回收"幂等标记

## 8. 前端（frontend-dev，合约确定后）

- [x] 8.1 `api/seckill.js`:活动列表/详情、抢购、结果查询(兜底)、我的秒杀订单;管理端 CRUD/上线/下线/手动重新预热(复用工作树残骸并对齐最终合约)
- [x] 8.2 `store/seckill.js`(Pinia Options 风格):活动列表/详情状态、抢购 action、SSE 连接管理 action(建立 `EventSource`/`fetch-event-source`、接收结果事件、断线时降级调用一次性查询兜底、组件卸载时关闭连接)
- [x] 8.3 `views/user/SeckillList.vue`(列表+倒计时组件)、`SeckillDetail.vue`(抢购按钮+SSE 结果监听:QUEUING→loading、SUCCESS→跳支付页、FAIL→提示、CAPTCHA_REQUIRED→弹 Turnstile;连接失败或超时降级一次性查询兜底)
- [x] 8.4 `views/admin/SeckillManage.vue`:活动 CRUD(表单含"是否允许二次流出"开关,默认开)+ 上线/下线 + 手动重新预热(上线已自动预热,此按钮用于开抢前需刷新库存快照的兜底场景)
- [x] 8.5 路由与入口:用户端与后台菜单接入 `router/index.js`,复用现有倒计时/EntityPicker 组件

## 9. 代码审查（code-reviewer，两位 dev 完成后，仅读）

- [x] 9.1 对照 `.claude/rules` 与安全红线:VO 不泄敏感字段、DO 不越界、SQL 在 XML、错误码规范、`.env`/密钥零改动
- [x] 9.2 并发正确性:分桶预扣不超卖、DB 写锁+乐观锁兜底、SETNX+唯一索引幂等、失败/异常路径回补完整
- [x] 9.3 **库存四处一致性闭环**:预留(上线)/扣减(消费)/回补(超时·失败)/归还(下线·结束)账面守恒,无重复回补
- [x] 9.4 前后端合约对接:接口路径/入参/返回/错误码/SSE 事件与降级查询状态机一致

## 10. 验收（acceptance-validator，审查通过后，不可跳过）

- [x] 10.1 L1 门禁:`mall-backend` `.\mvnw.cmd compile` 通过;`mall-frontend` `pnpm build` 通过
- [x] 10.2 L2 黑盒验收(按 specs 场景):活动创建/上线预留(含自动预热)/下线归还、抢购全防线(限流/风控/闸门/售罄/限购)、并发不超卖、中签落真实订单+`mall_seckill_order`快照→支付→超时未付按 source 回补(开启二次流出的活动回流后可再抢,关闭的活动超时释放库存不再可抢)、幂等去重、SSE 结果推送与断线降级查询
- [x] 10.3 输出验收报告:逐条 spec 场景通过/失败,附关键接口实测证据

## 11. 清理旧秒杀残骸（backend-dev/frontend-dev，验收通过后）

- [x] 11.1 验收通过后,删除工作树中半拆除、无法编译的旧秒杀残骸源码与视图(`design.md` Context 提及的残骸文件),`backup/seckill-3388fbf` 备份 tag 作为历史存档保留、不删除(结案说明:经全树盘点,33 个秒杀相关文件全部属于新实现声明清单——旧残骸均被原位重写/吸收,无孤儿文件可删,且无任何旧命名残留引用)
- [x] 11.2 删除后重新执行 L1 门禁(`.\mvnw.cmd compile` / `pnpm build`)确认无残留引用(最终代码状态下后端 clean compile exit 0、前端 build 通过均已复验)
