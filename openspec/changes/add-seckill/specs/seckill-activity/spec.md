## ADDED Requirements

### Requirement: 管理员创建秒杀活动
系统 SHALL 允许 ADMIN 角色创建秒杀活动,活动关联一个已上架商品(可选具体 SKU),并定义秒杀价、总库存、每人限购、起止时间、是否允许二次流出(`allow_restock`,控制超时释放库存能否重新放出,默认允许)。创建时活动默认处于草稿/下线状态(status=0),`available_stock` 初始化为 `seckill_stock`。

#### Scenario: 创建合法活动
- **WHEN** 管理员提交关联已上架商品、秒杀价不高于原价、结束时间晚于开始时间、秒杀库存不超过商品当前库存的活动
- **THEN** 系统创建活动,status=0,`available_stock` 等于 `seckill_stock`,返回活动详情

#### Scenario: 秒杀价高于原价被拒绝
- **WHEN** 管理员提交的 `seckillPrice` 大于 `originalPrice`
- **THEN** 系统抛出 `SECKILL_PRICE_INVALID`,不创建活动

#### Scenario: 结束时间不晚于开始时间被拒绝
- **WHEN** 管理员提交的 `endTime` 不晚于 `startTime`
- **THEN** 系统抛出 `SECKILL_TIME_INVALID`,不创建活动

#### Scenario: 秒杀库存超过商品库存被拒绝
- **WHEN** 管理员提交的 `seckillStock` 大于关联商品当前库存
- **THEN** 系统抛出 `SECKILL_STOCK_EXCEEDED`,不创建活动

#### Scenario: 关联商品不存在或已下架被拒绝
- **WHEN** 管理员提交的 `productId` 对应商品不存在或状态非上架
- **THEN** 系统抛出 `PRODUCT_NOT_FOUND` 或 `PRODUCT_UNAVAILABLE`,不创建活动

### Requirement: 管理员上线活动并预留库存
系统 SHALL 在活动上线(status 0→1)时,从关联商品库存中**真正预留** `seckill_stock` 数量:同一事务内扣减 `mall_product.stock` 并同步扣减商品 Redis 库存;预留提交后 SHALL 同步完成 Redis 预热(`available_stock` 均分写入分桶 + 初始化总量闸门),不依赖独立定时任务。预留失败(商品库存不足)时上线操作整体回滚,不触发预热。

#### Scenario: 上线成功并预留库存
- **WHEN** 管理员上线一个 status=0 且商品库存充足的活动
- **THEN** 系统将 `mall_product.stock` 扣减 `seckill_stock`,同步扣减商品 Redis 库存,活动 status 置为 1,并同步完成 Redis 分桶预热与闸门初始化

#### Scenario: 商品库存不足导致上线回滚
- **WHEN** 上线时商品当前库存小于 `seckill_stock`
- **THEN** 系统抛出 `SECKILL_STOCK_EXCEEDED`,商品库存与活动状态均不变,不触发预热

### Requirement: 管理员下线或删除活动并归还库存
系统 SHALL 在活动下线或删除时,将秒杀池中**未售出**的 `available_stock` 归还回 `mall_product.stock` 并同步恢复商品 Redis 库存,同时清除该活动的 Redis 秒杀库存/闸门/售罄标记等键。

#### Scenario: 下线活动归还未售库存
- **WHEN** 管理员下线一个已上线且尚有 `available_stock` 的活动
- **THEN** 系统将 `available_stock` 加回 `mall_product.stock`,恢复商品 Redis 库存,活动 status 置为 0,并清除活动相关 Redis 键

#### Scenario: 下线不存在的活动
- **WHEN** 管理员对不存在的 activityId 执行下线
- **THEN** 系统抛出 `SECKILL_ACTIVITY_NOT_FOUND`

### Requirement: 活动结束自动归还未售库存
系统 SHALL 在活动结束(超过 `endTime`)后归还该活动未售出的 `available_stock` 回商品库存,归还操作 MUST 幂等(重复执行不重复归还)。

#### Scenario: 结束后归还剩余库存
- **WHEN** 定时任务扫描到一个已过 `endTime` 且 `available_stock`>0 且尚未归还的活动
- **THEN** 系统将剩余库存归还商品库存并标记该活动库存已回收,再次扫描不再重复归还

### Requirement: 缓存预热
系统 SHALL 在活动上线时同步完成预热(`available_stock` 均分写入 Redis 分桶并初始化总量闸门),不依赖独立定时任务;并向管理员提供手动"重新预热"入口,用于开抢前需要以最新 `available_stock` 刷新 Redis 快照的场景。预热 MUST 覆盖式写入(手动重新预热总是以最新 `available_stock` 覆盖)。

#### Scenario: 手动预热已上线活动
- **WHEN** 管理员对一个 status=1 的活动触发手动重新预热
- **THEN** 系统将 `available_stock` 均分写入 Redis 分桶,初始化闸门为 `seckill_stock × 倍数`

#### Scenario: 预热未上线活动被拒绝
- **WHEN** 管理员对 status=0 的活动触发预热
- **THEN** 系统抛出 `SECKILL_ENDED`(提示"活动未上线,无法预热"),不写入 Redis

### Requirement: 用户查询秒杀活动
系统 SHALL 向用户端提供进行中与即将开始的活动列表,以及单个活动详情(含实时状态与倒计时所需的起止时间)。返回 MUST 为 VO,继承商品名称与主图,不包含敏感字段。

#### Scenario: 查询活动列表
- **WHEN** 用户请求秒杀活动列表
- **THEN** 系统返回进行中与即将开始的活动 VO 列表,每项含活动状态、秒杀价、原价、起止时间

#### Scenario: 查询不存在的活动详情
- **WHEN** 用户请求不存在的 activityId 详情
- **THEN** 系统抛出 `SECKILL_ACTIVITY_NOT_FOUND`
