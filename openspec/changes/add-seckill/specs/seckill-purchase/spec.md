## ADDED Requirements

### Requirement: 抢购接口限流
系统 SHALL 对抢购接口按用户与 IP 维度限流,超过阈值的请求被快速拒绝,以抵御脚本连发。

#### Scenario: 单用户高频连发被限流
- **WHEN** 同一用户在限流窗口内的抢购请求次数超过阈值
- **THEN** 系统拒绝超额请求(限流响应),不进入后续扣减流程

### Requirement: 抢购前活动状态与时间校验
系统 SHALL 在扣减库存前校验活动存在、已上线且处于 `startTime` 与 `endTime` 之间;不满足时快速失败且不消费库存或闸门额度。

#### Scenario: 活动未开始
- **WHEN** 当前时间早于活动 `startTime`
- **THEN** 系统抛出 `SECKILL_NOT_STARTED`

#### Scenario: 活动已结束或已下线
- **WHEN** 当前时间晚于 `endTime`,或活动 status 非上线
- **THEN** 系统抛出 `SECKILL_ENDED`

### Requirement: 风控与人机验证 step-up
系统 SHALL 对每次抢购入口记录用户与 IP 的频率计数;当频率或失败计数超过风险阈值时判定为高风险。高风险请求未携带有效人机验证令牌时 MUST 要求前端完成 Turnstile 人机验证,验证通过后清零该用户风险计数,风控判定 MUST 在扣减库存之前完成。

#### Scenario: 高风险请求要求验证码
- **WHEN** 请求被判定高风险且未携带 `cfToken`
- **THEN** 系统抛出 `SECKILL_CAPTCHA_REQUIRED`,不消费库存与闸门额度

#### Scenario: 携带有效令牌通过验证
- **WHEN** 高风险请求携带通过 Turnstile 校验的 `cfToken`
- **THEN** 系统清零该用户风险计数并放行进入后续流程

#### Scenario: 携带无效令牌被拒绝
- **WHEN** 高风险请求携带的 `cfToken` 未通过 Turnstile 校验
- **THEN** 系统抛出 `TURNSTILE_FAILED`

### Requirement: 总量闸门快速拒绝
系统 SHALL 用一个原子计数闸门限制进入后续扣减流程的请求总量为 `seckill_stock × 倍数`;超出闸门上限的请求被快速拒绝,以防过载。

#### Scenario: 超过闸门上限被拒绝
- **WHEN** 闸门计数已达到 `seckill_stock × 倍数` 后又有请求进入
- **THEN** 系统抛出 `SECKILL_GATE_REJECTED`,并累加该用户风险失败计数

### Requirement: 售罄标记快速失败
系统 SHALL 在库存扣光后设置售罄标记,后续请求命中标记时快速失败,避免无谓的 Redis 扣减尝试。

#### Scenario: 命中售罄标记
- **WHEN** 活动已被标记售罄,新请求到达
- **THEN** 系统抛出 `SECKILL_SOLD_OUT`,不再尝试分桶扣减

### Requirement: Redis 分桶原子预扣库存防超卖
系统 SHALL 将单活动库存拆分为 N 个 Redis 桶,抢购时以 Lua 脚本在随机起始桶做原子预扣;当前桶为空返回后 MUST 跨桶顺序重试,所有桶皆空时判定真实售罄并置售罄标记。预扣 MUST 保证并发下不超卖(累计成功扣减量不超过 `seckill_stock`)。库存键未预热时 MUST 懒加载后重试一次。

#### Scenario: 预扣成功
- **WHEN** 存在库存的桶被命中
- **THEN** 该桶库存原子减少,请求进入排队落单流程

#### Scenario: 当前桶空跨桶重试
- **WHEN** 随机起始桶已空但其他桶仍有库存
- **THEN** 系统顺序重试其余桶直到成功或全部为空

#### Scenario: 所有桶售罄
- **WHEN** 所有桶均无可用库存
- **THEN** 系统置售罄标记,抛出 `SECKILL_SOLD_OUT`

#### Scenario: 并发不超卖
- **WHEN** 并发请求总数远超库存
- **THEN** 预扣成功的请求总量不超过 `seckill_stock`,不出现负库存

#### Scenario: 库存未预热懒加载
- **WHEN** 抢购时活动 Redis 库存键不存在(未预热)
- **THEN** 系统懒加载 `available_stock` 到分桶后重试一次扣减

### Requirement: 每人限购
系统 SHALL 限制单用户在单活动内累计购买量不超过 `limit_per_user`;超过限购的请求快速失败,限购键为全局键(不随分桶变化)。

#### Scenario: 超过限购被拒绝
- **WHEN** 用户在该活动累计购买量加本次数量超过 `limit_per_user`
- **THEN** 系统抛出 `SECKILL_LIMIT_EXCEEDED`

### Requirement: 异步削峰与排队响应
系统 SHALL 在 Redis 预扣成功后,将落单请求投递到 MQ 异步处理,并立即向用户返回"排队中"结果、SSE 订阅入口与降级查询入口;抢购接口 MUST 不在请求线程内执行订单落库。

#### Scenario: 预扣成功进入排队
- **WHEN** Redis 预扣成功
- **THEN** 系统写入结果为 QUEUING,投递 MQ 落单消息(附幂等 requestId),立即返回排队中

#### Scenario: MQ 投递失败回补
- **WHEN** MQ 投递失败
- **THEN** 系统回补 Redis 库存并将结果置为失败,抛出 `SECKILL_SERVICE_UNAVAILABLE`

### Requirement: 抢购结果 SSE 推送与降级查询
系统 SHALL 提供按活动订阅抢购结果的 SSE 接口:用户收到"排队中"响应后建立 SSE 连接,MQ 消费者处理完成后(成功或失败)主动推送最终结果事件给该用户的连接。系统 SHALL 同时提供按活动与用户查询抢购结果的一次性接口,返回 QUEUING(排队中)、SUCCESS(含订单号)、FAIL(含原因)或未找到之一,供 SSE 未建立或连接中断时降级调用。

#### Scenario: SSE 推送成功结果
- **WHEN** 用户已建立 SSE 连接且中签落单成功
- **THEN** 系统通过该连接推送成功事件,携带订单号

#### Scenario: SSE 推送失败结果
- **WHEN** 用户已建立 SSE 连接且抢购最终失败
- **THEN** 系统通过该连接推送失败事件,携带失败原因

#### Scenario: 降级查询到排队中
- **WHEN** SSE 未连接或已断开,用户调用一次性查询接口且结果为 QUEUING
- **THEN** 接口返回排队中状态

#### Scenario: 降级查询到成功
- **WHEN** SSE 未连接或已断开,用户调用一次性查询接口且已中签落单
- **THEN** 接口返回成功状态与订单号

#### Scenario: 降级查询到失败
- **WHEN** SSE 未连接或已断开,用户调用一次性查询接口且抢购失败
- **THEN** 接口返回失败状态与失败原因
