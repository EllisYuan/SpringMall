-- ========================================
-- Mall Database Schema —— 唯一基线建表脚本
-- ----------------------------------------
-- 本脚本为全量建表基线，可在空库上一次性执行完成初始化。
-- 历史迁移脚本（V2 索引 / V3 归档表 / V4 SKU / V7 秒杀）已全部内联至此，不再单独维护。
--
-- 执行方式：
--   mysql -u root -p < schema.sql
--   （或由 docker-compose 挂载至 /docker-entrypoint-initdb.d/ 首次启动时自动执行）
--
-- 依赖：MySQL 8.0，且需 ngram_token_size=2（mall_product 全文索引使用 ngram 分词器）；
--       docker-compose 已在 command 中配置。
-- ========================================

-- 显式声明连接字符集，避免中文表注释被双重编码成乱码
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS mall DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mall;

-- ----------------------------------------
-- 1. 用户表
-- ----------------------------------------
CREATE TABLE `mall_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（加密）',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------------------
-- 2. 商品分类表（支持多级）
-- ----------------------------------------
CREATE TABLE `mall_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父分类ID，0为顶级',
    `level` INT NOT NULL DEFAULT 1 COMMENT '层级：1-一级 2-二级 3-三级',
    `sort_order` INT DEFAULT 0 COMMENT '排序值',
    `icon` VARCHAR(500) DEFAULT NULL COMMENT '分类图标',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ----------------------------------------
-- 3. 商品表
-- idx_name_prefix：优化 LIKE 'keyword%' 前缀匹配
-- ft_name_subtitle：ngram 全文索引，支持中文 2 字符分词
-- ----------------------------------------
CREATE TABLE `mall_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
    `subtitle` VARCHAR(500) DEFAULT NULL COMMENT '副标题',
    `main_image` VARCHAR(500) DEFAULT NULL COMMENT '主图URL',
    `images` TEXT DEFAULT NULL COMMENT '图片列表（JSON数组）',
    `detail` TEXT DEFAULT NULL COMMENT '商品详情（HTML）',
    `price` DECIMAL(10,2) NOT NULL COMMENT '价格',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-下架 1-上架',
    `sales_count` INT NOT NULL DEFAULT 0 COMMENT '累计销量',
    `has_sku` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用SKU：0-否 1-是',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_name_prefix` (`name`(50)),
    FULLTEXT KEY `ft_name_subtitle` (`name`, `subtitle`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ----------------------------------------
-- 4. 购物车表
-- sku_id 无SKU商品填 0：MySQL UNIQUE 约束对 NULL 无效，故不用 NULL
-- ----------------------------------------
CREATE TABLE `mall_cart_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '购物车ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `sku_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'SKU ID（无SKU商品为0）',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
    `checked` TINYINT NOT NULL DEFAULT 1 COMMENT '是否选中：0-否 1-是',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product_sku` (`user_id`, `product_id`, `sku_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- ----------------------------------------
-- 5. 收货地址表
-- ----------------------------------------
CREATE TABLE `mall_address` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '地址ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
    `phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
    `province` VARCHAR(50) NOT NULL COMMENT '省份',
    `city` VARCHAR(50) NOT NULL COMMENT '城市',
    `district` VARCHAR(50) NOT NULL COMMENT '区县',
    `detail_address` VARCHAR(200) NOT NULL COMMENT '详细地址',
    `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认：0-否 1-是',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- ----------------------------------------
-- 6. 订单主表
-- idx_user_id_status / idx_user_id_created_at：组合索引，避免 filesort
-- ----------------------------------------
CREATE TABLE `mall_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `total_amount` DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    `pay_amount` DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    `freight` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
    `status` VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '订单状态',
    `source` TINYINT NOT NULL DEFAULT 0 COMMENT '订单来源：0-普通 1-秒杀',
    `seckill_activity_id` BIGINT DEFAULT NULL COMMENT '秒杀活动ID（source=1 时有值）',
    `payment_method` VARCHAR(20) DEFAULT NULL COMMENT '支付方式：ALIPAY/WECHAT',
    `payment_time` DATETIME DEFAULT NULL COMMENT '支付时间',
    `ship_time` DATETIME DEFAULT NULL COMMENT '发货时间',
    `complete_time` DATETIME DEFAULT NULL COMMENT '完成时间',
    `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
    `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话',
    `receiver_address` VARCHAR(500) NOT NULL COMMENT '收货地址',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '订单备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_user_id_status` (`user_id`, `status`),
    KEY `idx_user_id_created_at` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ----------------------------------------
-- 7. 订单明细表
-- ----------------------------------------
CREATE TABLE `mall_order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `sku_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'SKU ID（无SKU商品为0）',
    `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称（冗余）',
    `product_image` VARCHAR(500) DEFAULT NULL COMMENT '商品图片（冗余）',
    `spec_desc` VARCHAR(500) DEFAULT NULL COMMENT '规格描述快照（如"红色,XL"）',
    `unit_price` DECIMAL(10,2) NOT NULL COMMENT '单价',
    `quantity` INT NOT NULL COMMENT '数量',
    `total_price` DECIMAL(10,2) NOT NULL COMMENT '总价',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_order_id_product_id` (`order_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ----------------------------------------
-- 8. 支付记录表
-- ----------------------------------------
CREATE TABLE `mall_payment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '支付ID',
    `payment_no` VARCHAR(50) NOT NULL COMMENT '支付流水号（唯一）',
    `order_no` VARCHAR(50) NOT NULL COMMENT '关联订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    `payment_method` VARCHAR(20) NOT NULL COMMENT '支付方式：ALIPAY/WECHAT/STRIPE',
    `payment_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '支付状态：PENDING-待支付/SUCCESS-成功/FAILED-失败/CLOSED-已关闭/REFUNDED-已退款',
    `trade_no` VARCHAR(200) DEFAULT NULL COMMENT '第三方交易号（Stripe Session ID 可能较长）',
    `code_url` VARCHAR(500) DEFAULT NULL COMMENT '支付二维码链接或 Stripe Checkout URL',
    `notify_time` DATETIME DEFAULT NULL COMMENT '异步通知时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_payment_status` (`payment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

-- ----------------------------------------
-- 9. 退款记录表
-- ----------------------------------------
CREATE TABLE `mall_refund` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '退款ID',
    `refund_no` VARCHAR(50) NOT NULL COMMENT '退款流水号（唯一）',
    `order_no` VARCHAR(50) NOT NULL COMMENT '关联订单号',
    `payment_no` VARCHAR(50) NOT NULL COMMENT '关联支付流水号',
    `trade_no` VARCHAR(200) DEFAULT NULL COMMENT '第三方交易号（Stripe Session ID 长度可达 100+ 字符）',
    `refund_amount` DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    `refund_reason` VARCHAR(500) DEFAULT NULL COMMENT '退款原因',
    `refund_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '退款状态：PENDING-处理中/PROCESSING-处理中/SUCCESS-成功/FAILED-失败',
    `refund_time` DATETIME DEFAULT NULL COMMENT '退款成功时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_payment_no` (`payment_no`),
    KEY `idx_refund_status` (`refund_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款记录表';

-- ----------------------------------------
-- 10. 商品规格维度表
-- ----------------------------------------
CREATE TABLE `mall_product_spec` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规格ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `name` VARCHAR(100) NOT NULL COMMENT '规格名称（如颜色、尺码）',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格维度表';

-- ----------------------------------------
-- 11. 商品规格选项值表
-- ----------------------------------------
CREATE TABLE `mall_product_spec_value` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规格值ID',
    `spec_id` BIGINT NOT NULL COMMENT '规格维度ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID（冗余，便于查询）',
    `value` VARCHAR(100) NOT NULL COMMENT '规格值（如红色、XL）',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_spec_id` (`spec_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格选项值表';

-- ----------------------------------------
-- 12. 商品SKU表
-- ----------------------------------------
CREATE TABLE `mall_sku` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `sku_code` VARCHAR(100) DEFAULT NULL COMMENT 'SKU编码',
    `spec_value_ids` VARCHAR(500) NOT NULL COMMENT '规格值ID组合（逗号分隔，升序排列）',
    `spec_desc` VARCHAR(500) NOT NULL COMMENT '规格描述冗余（如"红色,XL"）',
    `price` DECIMAL(10,2) NOT NULL COMMENT 'SKU价格',
    `stock` INT NOT NULL DEFAULT 0 COMMENT 'SKU库存',
    `image` VARCHAR(500) DEFAULT NULL COMMENT 'SKU图片',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认SKU：0-否 1-是',
    `sales_count` INT NOT NULL DEFAULT 0 COMMENT 'SKU销量',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    UNIQUE KEY `uk_product_spec_values` (`product_id`, `spec_value_ids`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

-- ----------------------------------------
-- 13. 秒杀活动表（活动/库存权威）
-- 主键为应用层雪花 ID（SnowflakeIdGenerator 生成，非 AUTO_INCREMENT），
-- 防止对外接口暴露可枚举的自增 ID 泄露业务量。
-- ----------------------------------------
CREATE TABLE `mall_seckill_activity` (
    `id` BIGINT NOT NULL COMMENT '活动ID（应用层雪花生成，非自增）',
    `product_id` BIGINT NOT NULL COMMENT '关联商品ID',
    `sku_id` BIGINT NOT NULL DEFAULT 0 COMMENT '关联SKU ID，无SKU时为0',
    `activity_name` VARCHAR(200) NOT NULL COMMENT '活动名称',
    `original_price` DECIMAL(10,2) NOT NULL COMMENT '原价（快照）',
    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    `seckill_stock` INT NOT NULL COMMENT '秒杀总库存',
    `available_stock` INT NOT NULL COMMENT '剩余可用库存（DB乐观锁兜底权威）',
    `limit_per_user` INT NOT NULL DEFAULT 1 COMMENT '每人限购数量',
    `start_time` DATETIME NOT NULL COMMENT '活动开始时间',
    `end_time` DATETIME NOT NULL COMMENT '活动结束时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-下线/草稿 1-上线',
    `allow_restock` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '超时释放库存是否允许二次流出：1-允许（回流可再抢） 0-不允许（仅账面回补）',
    `stock_reclaimed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '预留库存已归还商品库存的幂等标记：0-未归还 1-已归还',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_end` (`status`, `end_time`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

-- ----------------------------------------
-- 14. 秒杀订单明细快照表
-- 与 mall_order 1:1（uk_order_id），承载秒杀维度快照与幂等/限购统计；
-- request_id 唯一索引兜底 MQ 重复消费幂等。主键同为雪花 ID。
-- ----------------------------------------
CREATE TABLE `mall_seckill_order` (
    `id` BIGINT NOT NULL COMMENT '主键ID（应用层雪花生成，非自增）',
    `order_id` BIGINT NOT NULL COMMENT '关联 mall_order.id（1:1）',
    `order_no` VARCHAR(50) NOT NULL COMMENT '订单号（冗余，便于秒杀维度查询直达支付）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `activity_id` BIGINT NOT NULL COMMENT '秒杀活动ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `sku_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'SKU ID，无SKU时为0',
    `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称快照',
    `product_image` VARCHAR(500) DEFAULT NULL COMMENT '商品主图快照',
    `spec_desc` VARCHAR(500) DEFAULT NULL COMMENT '规格描述快照（如"红色,XL"）',
    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀单价快照',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    `request_id` VARCHAR(64) NOT NULL COMMENT '请求幂等ID（UUID，配合唯一索引防重复消费）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`),
    UNIQUE KEY `uk_order_id` (`order_id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_activity_user` (`activity_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单明细快照表';

-- ----------------------------------------
-- 15. 归档表（冷热数据分离）
-- 结构由 LIKE 复制自对应主表，随主表演进自动保持一致。
-- ----------------------------------------
CREATE TABLE `mall_order_archive` LIKE `mall_order`;
CREATE TABLE `mall_order_item_archive` LIKE `mall_order_item`;
CREATE TABLE `mall_payment_archive` LIKE `mall_payment`;
CREATE TABLE `mall_refund_archive` LIKE `mall_refund`;

-- 归档表额外索引（针对历史查询场景）
ALTER TABLE `mall_order_archive` ADD INDEX `idx_archive_user_created` (`user_id`, `created_at`);
ALTER TABLE `mall_order_archive` ADD INDEX `idx_archive_order_no` (`order_no`);

-- ----------------------------------------
-- 初始数据
-- ----------------------------------------

-- 默认 admin 账户（密码: admin123）
INSERT INTO `mall_user` (`username`, `password`, `email`, `role`, `status`) VALUES
('admin', '$2a$10$uISN1BbnQjhKwrx4twz31.X/8cdzxjO.4hvYMPfbWnpYdBlmWgX0G', 'admin@mall.com', 'ADMIN', 1);
-- 默认 user 账户（密码: 123456）
INSERT INTO `mall_user` (`username`, `password`, `email`, `role`, `status`) VALUES
('user', '$2a$10$sVu5LD/7oO8OMJ1wyGy6gueaIwyXdizADClstPIxw5Ux7hbmYLlry', 'user@mall.com', 'USER', 1);

-- 示例分类
INSERT INTO `mall_category` (`name`, `parent_id`, `level`, `sort_order`) VALUES
('实体商品', 0, 1, 1),
('虚拟商品', 1, 1, 2);
