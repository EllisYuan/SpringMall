-- ----------------------------------------
-- V4: SKU 支持
-- ----------------------------------------
-- 新增商品规格表
-- 回滚: DROP TABLE IF EXISTS `mall_product_spec`;
CREATE TABLE IF NOT EXISTS `mall_product_spec` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规格ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `name` VARCHAR(100) NOT NULL COMMENT '规格名称（如颜色、尺寸）',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格维度表';

-- 新增商品规格值表
-- 回滚: DROP TABLE IF EXISTS `mall_product_spec_value`;
CREATE TABLE IF NOT EXISTS `mall_product_spec_value` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规格值ID',
    `spec_id` BIGINT NOT NULL COMMENT '规格ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `value` VARCHAR(100) NOT NULL COMMENT '规格值（如红色、XL）',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_spec_id` (`spec_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格值表';

-- 新增商品SKU表
-- 回滚: DROP TABLE IF EXISTS `mall_sku`;
CREATE TABLE IF NOT EXISTS `mall_sku` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `sku_code` VARCHAR(100) DEFAULT NULL COMMENT 'SKU编码',
    `spec_value_ids` VARCHAR(500) NOT NULL COMMENT '规格值ID组合（逗号分隔，升序排列）',
    `spec_desc` VARCHAR(500) NOT NULL COMMENT '规格描述冗余（如"红色,XL"）',
    `price` DECIMAL(10,2) NOT NULL COMMENT 'SKU价格',
    `stock` INT NOT NULL DEFAULT 0 COMMENT 'SKU库存',
    `image` VARCHAR(500) DEFAULT NULL COMMENT 'SKU图片',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `sales_count` INT NOT NULL DEFAULT 0 COMMENT 'SKU销量',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    UNIQUE KEY `uk_product_spec_values` (`product_id`, `spec_value_ids`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

-- mall_product 新增 has_sku 字段
-- 回滚: ALTER TABLE `mall_product` DROP COLUMN `has_sku`;
ALTER TABLE `mall_product` ADD COLUMN IF NOT EXISTS `has_sku` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用SKU：0-否 1-是' AFTER `sales_count`;

-- mall_cart_item 新增 sku_id 字段，修改唯一约束
-- 回滚: ALTER TABLE `mall_cart_item` DROP INDEX `uk_user_product_sku`;
ALTER TABLE `mall_cart_item` ADD COLUMN IF NOT EXISTS `sku_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'SKU ID，无SKU时为0' AFTER `product_id`;
ALTER TABLE `mall_cart_item` DROP INDEX IF EXISTS `uk_user_product`;
ALTER TABLE `mall_cart_item` ADD UNIQUE KEY IF NOT EXISTS `uk_user_product_sku` (`user_id`, `product_id`, `sku_id`);

-- ----------------------------------------
-- 新增 is_default 字段：标记默认SKU
-- 回滚: ALTER TABLE `mall_sku` DROP COLUMN `is_default`;
-- ----------------------------------------
ALTER TABLE mall_sku ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认SKU：0-否 1-是' AFTER status;
