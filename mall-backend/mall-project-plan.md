# Spring Mall BACK-END方案

> 最后更新时间：2026-04-05

## 一、项目概述

### 1.1 项目名称
Mall Backend API - 小型在线商城后端

### 1.2 技术栈

| 类别 | 技术选型 | 版本 |
|------|----------|----------|
| 框架 | Spring Boot | 3.2.5 |
| Web | Spring MVC | 6.1.6 |
| 持久层 | MyBatis | 3.5.16 |
| 数据库 | MySQL | 8.0.37 |
| 安全 | Spring Security + JWT |  |
| 文档 | springdoc-openapi-starter-webmvc-ui | 2.3.0 |
| 构建 | Maven | 3.9.6 |
| JDK | Java | 21 |

### 1.3 核心功能模块

```
┌─────────────────────────────────────────────────────────────┐
│                      Mall Backend API                        │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │  用户模块   │  │  商品模块   │  │  分类模块   │          │
│  │  - 注册     │  │  - 列表     │  │  - 列表     │          │
│  │  - 登录     │  │  - 详情     │  │  - 树结构   │          │
│  │  - 登出     │  │  - 搜索     │  │             │          │
│  │  - 信息管理 │  │  - 筛选     │  │             │          │
│  └─────────────┘  └─────────────┘  └─────────────┘          │
│                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │  购物车     │  │  订单模块   │  │  地址模块   │          │
│  │  - 添加     │  │  - 创建     │  │  - CRUD     │          │
│  │  - 修改     │  │  - 列表     │  │  - 默认地址 │          │
│  │  - 删除     │  │  - 详情     │  │             │          │
│  │  - 清空     │  │  - 取消     │  │             │          │
│  └─────────────┘  └─────────────┘  └─────────────┘          │
│                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │  支付模块   │  │  库存管理   │  │  后台管理   │          │
│  │  - 模拟支付 │  │  - 下单扣减 │  │  - 商品管理 │          │
│  │  - 支付宝   │  │  - 取消恢复 │  │  - 订单管理 │          │
│  │  - 微信支付 │  │  - SKU库存  │  │  - 用户管理 │          │
│  │  - Stripe   │  │  - Redis预扣│  │  - SKU管理  │          │
│  └─────────────┘  └─────────────┘  └─────────────┘          │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、角色与权限设计

### 2.1 角色定义

| 角色 | 标识 | 说明 |
|------|------|------|
| 普通用户 | USER | 购物、下单、管理个人信息和地址 |
| 管理员 | ADMIN | 商品CRUD、订单管理、用户管理、查看所有数据 |

### 2.2 权限矩阵

| 功能模块 | USER | ADMIN |
|----------|------|-------|
| 注册/登录 | ✓ | ✓ |
| 个人信息管理 | ✓ | ✓ |
| 浏览商品 | ✓ | ✓ |
| 购物车操作 | ✓ | ✗ |
| 下单/订单查询 | ✓ | ✗ |
| 地址管理 | ✓ | ✗ |
| 商品管理(CRUD) | ✗ | ✓ |
| 所有订单管理 | ✗ | ✓ |
| 用户管理 | ✗ | ✓ |
| 分类管理 | ✗ | ✓ |

---

## 三、订单状态流转

### 3.1 状态定义

| 状态 | 标识 | 说明 |
|------|------|------|
| 待支付 | UNPAID | 订单创建，等待支付 |
| 已支付 | PAID | 支付成功，等待发货 |
| 已发货 | SHIPPED | 商品已发出 |
| 已完成 | COMPLETED | 用户确认收货 |
| 已取消 | CANCELLED | 订单取消（仅待支付可取消） |

### 3.2 状态流转图

```
                    ┌──────────────┐
                    │   创建订单   │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
          ┌─────────│    UNPAID    │─────────┐
          │         │   待支付     │         │
          │         └──────┬───────┘         │
          │                │                 │
          │ 用户取消       │ 支付成功        │
          │                │                 │
          ▼                ▼                 │
   ┌──────────────┐ ┌──────────────┐         │
   │  CANCELLED   │ │     PAID     │         │
   │   已取消     │ │    已支付    │         │
   └──────────────┘ └──────┬───────┘         │
                           │                 │
                           │ 管理员发货      │
                           │                 │
                           ▼                 │
                    ┌──────────────┐         │
                    │   SHIPPED    │         │
                    │   已发货     │         │
                    └──────┬───────┘         │
                           │                 │
                           │ 确认收货        │
                           │                 │
                           ▼                 │
                    ┌──────────────┐         │
                    │  COMPLETED   │         │
                    │   已完成     │         │
                    └──────────────┘         │
```

### 3.3 库存策略

- **双层库存模型**：
  - 无 SKU 商品 → `mall_product.stock` 维度
  - 有 SKU 商品 → `mall_sku.stock` 维度
- **下单时**：Redis 预扣（仅无 SKU 商品）+ DB 乐观锁扣减库存
- **取消时**：区分 SKU / Product 维度恢复库存
- **掉单补偿**：延迟队列超时后，区分 SKU / Product 维度恢复库存
- **库存不足**：拒绝下单（Redis 快速拒绝 + DB 兜底校验）

---

## 四、数据库设计

### 4.1 ER 关系图

```
┌─────────────────┐       ┌─────────────────┐
│   mall_user     │       │  mall_category  │
├─────────────────┤       ├─────────────────┤
│ id (PK)         │       │ id (PK)         │
│ username        │       │ name            │
│ password        │       │ parent_id (FK)  │──┐
│ email           │       │ level           │  │
│ phone           │       │ sort_order      │  │
│ avatar          │       │ status          │  │
│ role            │       └────────┬────────┘  │
│ status          │                │           │
│ created_at      │                └───────────┘
│ updated_at      │                     │
└────────┬────────┘                     │
         │                              │
         │ 1:N                          │ 1:N
         │                              │
         ▼                              ▼
┌─────────────────┐       ┌─────────────────┐
│  mall_address   │       │  mall_product   │
├─────────────────┤       ├─────────────────┤
│ id (PK)         │       │ id (PK)         │
│ user_id (FK)    │       │ category_id(FK) │
│ receiver_name   │       │ name            │
│ phone           │       │ subtitle        │
│ province        │       │ main_image      │
│ city            │       │ images          │
│ district        │       │ detail          │
│ detail_address  │       │ price           │
│ is_default      │       │ stock           │
│ created_at      │       │ sales_count     │
│ updated_at      │       │ has_sku         │
└─────────────────┘       │ status          │
         │                │ created_at      │
         │                │ updated_at      │
         │                └────────┬────────┘
         │                         │
         │                    1:N  │  1:N
         │               ┌─────────┴─────────┐
         │               │                   │
         │               ▼                   ▼
         │   ┌──────────────────┐  ┌─────────────────┐
         │   │mall_product_spec │  │    mall_sku      │
         │   ├──────────────────┤  ├─────────────────┤
         │   │ id (PK)          │  │ id (PK)         │
         │   │ product_id (FK)  │  │ product_id (FK) │
         │   │ name             │  │ sku_code        │
         │   │ sort_order       │  │ spec_value_ids  │
         │   └────────┬─────── ┘  │ spec_desc       │
         │            │ 1:N       │ price           │
         │            ▼           │ stock           │
         │   ┌──────────────────┐ │ image           │
         │   │mall_product_     │ │ status          │
         │   │  spec_value      │ │ is_default      │
         │   ├──────────────────┤ │ sales_count     │
         │   │ id (PK)          │ └─────────────────┘
         │   │ spec_id (FK)     │
         │   │ product_id (FK)  │
         │   │ value            │
         │   │ sort_order       │
         │   └──────────────────┘
         │
         │                ┌─────────────────┐
         │                │ mall_cart_item  │
         │                ├─────────────────┤
         │                │ id (PK)         │
         │                │ user_id (FK)    │◄────┐
         │                │ product_id (FK) │     │
         │                │ sku_id (FK)     │     │
         │                │ quantity        │     │
         │                │ checked         │     │
         │                │ created_at      │     │
         │                │ updated_at      │     │
         │                └─────────────────┘     │
         │                                        │
         │                         ┌──────────────┘
         │                         │
         ▼                         │
┌─────────────────┐                │
│   mall_order    │                │
├─────────────────┤                │
│ id (PK)         │                │
│ order_no        │ (唯一订单号)    │
│ user_id (FK)    │◄───────────────┘
│ total_amount    │
│ pay_amount      │
│ freight         │
│ status          │
│ payment_time    │
│ ship_time       │
│ complete_time   │
│ receiver_name   │
│ receiver_phone  │
│ receiver_addr   │
│ created_at      │
│ updated_at      │
└────────┬────────┘
         │
         │ 1:N
         │
         ▼
┌─────────────────┐
│mall_order_item  │
├─────────────────┤
│ id (PK)         │
│ order_id (FK)   │
│ product_id (FK) │
│ sku_id (FK)     │ (无SKU时为0)
│ product_name    │ (冗余，防止商品变动)
│ product_image   │
│ spec_desc       │ (规格描述快照)
│ unit_price      │
│ quantity        │
│ total_price     │
│ created_at      │
└─────────────────┘
```

### 4.2 数据表清单

| 序号 | 表名 | 说明 |
|------|------|------|
| 1 | mall_user | 用户表 |
| 2 | mall_category | 商品分类表（支持多级） |
| 3 | mall_product | 商品表 |
| 4 | mall_product_spec | 商品规格维度表（颜色、尺寸等） |
| 5 | mall_product_spec_value | 商品规格值表（红色、XL 等） |
| 6 | mall_sku | 商品SKU表（规格组合 → 价格/库存） |
| 7 | mall_cart_item | 购物车表（新增 sku_id 字段） |
| 8 | mall_address | 收货地址表 |
| 9 | mall_order | 订单主表 |
| 10 | mall_order_item | 订单明细表（新增 sku_id、spec_desc） |
| 11 | mall_payment | 支付记录表 |
| 12 | mall_refund | 退款记录表 |

### 4.3 建表 SQL

```sql
-- ========================================
-- Mall Database Schema
-- ========================================

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
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ----------------------------------------
-- 4. 购物车表
-- ----------------------------------------
CREATE TABLE `mall_cart_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '购物车ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
    `checked` TINYINT NOT NULL DEFAULT 1 COMMENT '是否选中：0-否 1-是',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
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
-- ----------------------------------------
CREATE TABLE `mall_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `total_amount` DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    `pay_amount` DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    `freight` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
    `status` VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '订单状态',
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
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ----------------------------------------
-- 7. 订单明细表
-- ----------------------------------------
CREATE TABLE `mall_order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称（冗余）',
    `product_image` VARCHAR(500) DEFAULT NULL COMMENT '商品图片（冗余）',
    `unit_price` DECIMAL(10,2) NOT NULL COMMENT '单价',
    `quantity` INT NOT NULL COMMENT '数量',
    `total_price` DECIMAL(10,2) NOT NULL COMMENT '总价',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ----------------------------------------
-- 初始数据
-- ----------------------------------------

-- 插入管理员账户（密码: admin123，需使用BCrypt加密后的值）
INSERT INTO `mall_user` (`username`, `password`, `email`, `role`, `status`) VALUES
('admin', '$2a$10$uISN1BbnQjhKwrx4twz31.X/8cdzxjO.4hvYMPfbWnpYdBlmWgX0G', 'admin@mall.com', 'ADMIN', 1);

-- 插入示例分类
INSERT INTO `mall_category` (`name`, `parent_id`, `level`, `sort_order`) VALUES
('电子产品', 0, 1, 1),
('服装', 0, 1, 2),
('食品', 0, 1, 3),
('手机', 1, 2, 1),
('电脑', 1, 2, 2),
('男装', 2, 2, 1),
('女装', 2, 2, 2);
```

---

## 五、API 接口设计

### 5.1 接口规范

**基础路径**: `/api/v1`

**统一响应格式**:
```json
{
    "code": 200,
    "message": "success",
    "data": { }
}
```

**错误响应格式**:
```json
{
    "code": 40001,
    "message": "用户名已存在",
    "data": null
}
```

**分页响应格式**:
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "list": [],
        "total": 100,
        "page": 1,
        "size": 10,
        "pages": 10
    }
}
```

### 5.2 接口清单

#### 5.2.1 认证模块 `/api/v1/auth`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /register | 用户注册 | 公开 |
| POST | /login | 用户登录 | 公开 |
| POST | /logout | 用户登出 | 登录 |
| POST | /refresh | 刷新Token | 登录 |

#### 5.2.2 用户模块 `/api/v1/user`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /profile | 获取个人信息 | USER |
| PUT | /profile | 修改个人信息 | USER |
| PUT | /password | 修改密码 | USER |

#### 5.2.3 商品模块 `/api/v1/products`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | / | 商品列表（分页） | 公开 |
| GET | /{id} | 商品详情 | 公开 |
| GET | /search | 搜索商品 | 公开 |
| GET | /category/{categoryId} | 按分类查询 | 公开 |

#### 5.2.4 分类模块 `/api/v1/categories`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | / | 分类列表 | 公开 |
| GET | /tree | 分类树结构 | 公开 |
| GET | /{id} | 分类详情 | 公开 |

#### 5.2.5 购物车模块 `/api/v1/cart`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | / | 获取购物车列表 | USER |
| POST | / | 添加商品到购物车（支持 skuId） | USER |
| PUT | /{id} | 修改购物车商品数量 | USER |
| DELETE | /{id} | 删除购物车商品 | USER |
| DELETE | / | 清空购物车 | USER |
| PUT | /check/{id} | 选中/取消选中 | USER |
| PUT | /check-all | 全选/取消全选 | USER |

#### 5.2.6 收货地址模块 `/api/v1/addresses`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | / | 地址列表 | USER |
| GET | /{id} | 地址详情 | USER |
| POST | / | 新增地址 | USER |
| PUT | /{id} | 修改地址 | USER |
| DELETE | /{id} | 删除地址 | USER |
| PUT | /{id}/default | 设为默认地址 | USER |

#### 5.2.7 订单模块 `/api/v1/orders`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | / | 创建订单 | USER |
| GET | / | 订单列表 | USER |
| GET | /{orderNo} | 订单详情 | USER |
| PUT | /{orderNo}/cancel | 取消订单 | USER |
| PUT | /{orderNo}/confirm | 确认收货 | USER |

#### 5.2.8 支付模块 `/api/v1/payment`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /pay | 发起支付（模拟） | USER |
| POST | /notify | 支付回调（模拟） | 内部 |
| POST | /alipay/create | 创建支付宝支付 | USER |
| POST | /alipay/notify | 支付宝异步通知 | 公开 |
| GET | /alipay/return | 支付宝同步返回 | 公开 |
| POST | /wechat/native | 创建微信Native支付 | USER |
| POST | /wechat/notify | 微信支付异步通知 | 公开 |
| POST | /wechat/refund | 微信退款 | USER |
| POST | /wechat/refund/notify | 微信退款异步通知 | 公开 |
| POST | /stripe/create | 创建Stripe支付 | USER |
| GET | /stripe/{paymentNo} | 查询Stripe支付状态 | USER |
| POST | /stripe/webhook | Stripe支付Webhook | 公开 |
| POST | /stripe/refund | Stripe退款 | ADMIN |
| POST | /stripe/refund/webhook | Stripe退款Webhook | 公开 |
| GET | /{paymentNo} | 查询支付状态 | USER |

#### 5.2.9 后台管理 - 商品 `/api/v1/admin/products`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | / | 商品列表（分页） | ADMIN |
| GET | /{id} | 商品详情 | ADMIN |
| POST | / | 新增商品 | ADMIN |
| PUT | /{id} | 修改商品 | ADMIN |
| DELETE | /{id} | 删除商品 | ADMIN |
| PUT | /{id}/status | 上下架商品 | ADMIN |
| PUT | /{id}/stock | 修改库存 | ADMIN |
| PUT | /{id}/sku-config | 保存SKU配置（全量替换） | ADMIN |
| GET | /{id}/sku-config | 获取SKU配置 | ADMIN |
| DELETE | /{id}/sku-config | 删除SKU配置 | ADMIN |

#### 5.2.10 后台管理 - 分类 `/api/v1/admin/categories`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | / | 新增分类 | ADMIN |
| PUT | /{id} | 修改分类 | ADMIN |
| DELETE | /{id} | 删除分类 | ADMIN |
| PUT | /{id}/status | 启用/禁用分类 | ADMIN |

#### 5.2.11 后台管理 - 订单 `/api/v1/admin/orders`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | / | 所有订单列表 | ADMIN |
| GET | /status/{status} | 按状态查询订单 | ADMIN |
| GET | /{orderNo} | 订单详情 | ADMIN |
| PUT | /{orderNo}/ship | 订单发货 | ADMIN |
| PUT | /{orderNo}/cancel | 管理员取消订单 | ADMIN |

#### 5.2.12 后台管理 - 用户 `/api/v1/admin/users`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | / | 用户列表 | ADMIN |
| GET | /{id} | 用户详情 | ADMIN |
| PUT | /{id}/status | 启用/禁用用户 | ADMIN |

---

## 六、项目结构

```
mall-backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/mall/
│   │   │   ├── MallApplication.java              # 启动类
│   │   │   │
│   │   │   ├── config/                           # 配置类
│   │   │   │   ├── MyBatisConfig.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── SwaggerConfig.java
│   │   │   │   └── WebMvcConfig.java
│   │   │   │
│   │   │   ├── security/                         # 安全相关
│   │   │   │   ├── JwtTokenProvider.java
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   ├── UserDetailsServiceImpl.java
│   │   │   │   └── SecurityUser.java
│   │   │   │
│   │   │   ├── controller/                       # 控制器
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── UserController.java
│   │   │   │   ├── ProductController.java
│   │   │   │   ├── CategoryController.java
│   │   │   │   ├── CartController.java
│   │   │   │   ├── AddressController.java
│   │   │   │   ├── OrderController.java
│   │   │   │   ├── PaymentController.java
│   │   │   │   └── admin/
│   │   │   │       ├── AdminProductController.java
│   │   │   │       ├── AdminCategoryController.java
│   │   │   │       ├── AdminOrderController.java
│   │   │   │       └── AdminUserController.java
│   │   │   │
│   │   │   ├── service/                          # 业务逻辑层
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── UserService.java
│   │   │   │   ├── ProductService.java
│   │   │   │   ├── CategoryService.java
│   │   │   │   ├── CartService.java
│   │   │   │   ├── AddressService.java
│   │   │   │   ├── OrderService.java
│   │   │   │   ├── PaymentService.java
│   │   │   │   └── impl/
│   │   │   │       ├── AuthServiceImpl.java
│   │   │   │       ├── UserServiceImpl.java
│   │   │   │       ├── ProductServiceImpl.java
│   │   │   │       ├── CategoryServiceImpl.java
│   │   │   │       ├── CartServiceImpl.java
│   │   │   │       ├── AddressServiceImpl.java
│   │   │   │       ├── OrderServiceImpl.java
│   │   │   │       └── PaymentServiceImpl.java
│   │   │   │
│   │   │   ├── mapper/                           # MyBatis Mapper接口
│   │   │   │   ├── UserMapper.java
│   │   │   │   ├── ProductMapper.java
│   │   │   │   ├── CategoryMapper.java
│   │   │   │   ├── CartItemMapper.java
│   │   │   │   ├── AddressMapper.java
│   │   │   │   ├── OrderMapper.java
│   │   │   │   └── OrderItemMapper.java
│   │   │   │
│   │   │   ├── entity/                           # 实体类
│   │   │   │   ├── User.java
│   │   │   │   ├── Product.java
│   │   │   │   ├── Category.java
│   │   │   │   ├── CartItem.java
│   │   │   │   ├── Address.java
│   │   │   │   ├── Order.java
│   │   │   │   └── OrderItem.java
│   │   │   │
│   │   │   ├── dto/                              # 数据传输对象
│   │   │   │   ├── request/
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   ├── ProductRequest.java
│   │   │   │   │   ├── CartItemRequest.java
│   │   │   │   │   ├── AddressRequest.java
│   │   │   │   │   └── OrderRequest.java
│   │   │   │   └── response/
│   │   │   │       ├── LoginResponse.java
│   │   │   │       ├── UserResponse.java
│   │   │   │       ├── ProductResponse.java
│   │   │   │       ├── CartResponse.java
│   │   │   │       ├── AddressResponse.java
│   │   │   │       └── OrderResponse.java
│   │   │   │
│   │   │   ├── common/                           # 通用类
│   │   │   │   ├── Result.java                   # 统一响应
│   │   │   │   ├── PageResult.java               # 分页响应
│   │   │   │   ├── ResultCode.java               # 响应码枚举
│   │   │   │   └── OrderStatus.java              # 订单状态枚举
│   │   │   │
│   │   │   ├── exception/                        # 异常处理
│   │   │   │   ├── BusinessException.java
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │
│   │   │   └── util/                             # 工具类
│   │   │       ├── OrderNoGenerator.java
│   │   │       └── PageHelper.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml                   # 主配置
│   │       ├── application-dev.yml               # 开发环境配置
│   │       ├── application-prod.yml              # 生产环境配置
│   │       ├── mapper/                           # MyBatis XML
│   │       │   ├── UserMapper.xml
│   │       │   ├── ProductMapper.xml
│   │       │   ├── CategoryMapper.xml
│   │       │   ├── CartItemMapper.xml
│   │       │   ├── AddressMapper.xml
│   │       │   ├── OrderMapper.xml
│   │       │   └── OrderItemMapper.xml
│   │       └── db/
│   │           └── schema.sql                    # 建表SQL
│   │
│   └── test/                                     # 测试代码
│       └── java/com/mall/
│           └── ...
```

---

## 七、已完成功能

### 7.1 支付功能模块（2026-02-15 ~ 2026-02-16）

#### 1. 支付宝支付集成
- ✅ 支付宝沙箱环境配置（`AlipayConfig`）
- ✅ PC 网页支付接口（`alipay.trade.page.pay`）
- ✅ 支付结果查询（`GET /api/v1/payment/{paymentNo}`）
- ✅ 异步通知处理（签名验证 + 订单状态更新）
- ✅ 同步返回处理（重定向到前端结果页）
- ✅ 支付记录管理（`mall_payment` 表）
- ✅ 支付流水号生成（`PY` + 时间戳 + 随机数）

**技术实现**：
- SDK：`alipay-sdk-java:4.40.658.ALL`（v2 协议）
- 配置：Spring Boot + `@Value` 环境变量注入
- 安全：签名验证（RSA2）+ 金额校验 + 幂等性保证

**API 接口**：
- `POST /api/v1/payment/alipay/create` - 创建支付宝支付
- `GET /api/v1/payment/{paymentNo}` - 查询支付状态
- `POST /api/v1/payment/alipay/notify` - 支付宝异步通知回调
- `GET /api/v1/payment/alipay/return` - 支付宝同步返回

---

#### 1.1 微信支付集成
- ✅ 微信Native扫码支付集成（`WxPayConfig`）
- ✅ 官方SDK使用（`wechatpay-java:0.2.17`）
- ✅ 自动签名验签（RSAAutoCertificateConfig）
- ✅ 支付二维码生成（Native支付模式）
- ✅ 异步通知处理（自动解密 + 订单状态更新）
- ✅ 退款功能（全额退款 + 异步通知）
- ✅ 条件启用（`wxpay.enabled`开关控制）

**技术实现**：
- SDK：`wechatpay-java:0.2.17`（官方推荐，Service层抽象）
- 配置：Spring Boot + `@ConditionalOnProperty` 条件加载
- 签名：RSA 2048-bit，自动证书管理
- 私钥：支持 classpath 资源和文件系统路径
- 回调：NotificationParser 自动验签解密

**配置说明**：
- 微信支付无沙箱环境，默认禁用（`wxpay.enabled=false`）
- 启用需配置真实商户号、APIv3密钥、证书序列号、私钥文件
- 私钥文件路径：`classpath:certs/wechatpay_private_key.pem`

**API 接口**：
- `POST /api/v1/payment/wechat/native` - 创建微信Native支付
- `POST /api/v1/payment/wechat/notify` - 微信支付异步通知
- `POST /api/v1/payment/wechat/refund` - 微信退款
- `POST /api/v1/payment/wechat/refund/notify` - 微信退款异步通知

**数据库变更**：
- `mall_payment` 表新增 `code_url` 字段（存储微信支付二维码链接）
- 迁移脚本：`V1.2__add_wechat_payment_support.sql`

---

#### 2. 订单退款功能
- ✅ 支付宝退款接口封装（`alipay.trade.refund`）
- ✅ 退款业务逻辑（`PaymentService.refundOrder()`）
- ✅ 订单取消自动退款（取消已支付订单时自动调用）
- ✅ 退款记录管理（`mall_refund` 表）
- ✅ 退款流水号生成（`RF` + 时间戳 + 随机数）

**安全特性**：
- 三重幂等性保护（支付状态 + 退款记录 + 支付宝去重）
- 事务一致性（退款失败时整个取消操作回滚）
- 金额验证（退款金额从支付记录获取，防篡改）

**业务流程**：
```
取消已支付订单 → 查询支付记录 → 幂等性检查
→ 调用支付宝退款接口 → 创建退款记录
→ 更新支付状态（REFUNDED）→ 恢复库存 → 订单状态变为 CANCELLED
```

---

#### 3. 支付商品显示优化
- ✅ 支付标题优化（显示商品名称而非"商城订单"）
- ✅ 支付描述优化（显示商品明细和总金额）
- ✅ 单/多商品智能显示
- ✅ 超长商品名自动截断（subject ≤ 256字符，body ≤ 128字符）

**优化效果**：
- **优化前**：`"商城订单-20260215165735740624"`
- **优化后**：
  - 单商品：`"Redmi K50 Pro x1 - 订单20260215165735740624"`
  - 多商品：`"Redmi K50 Pro等2件商品 - 订单20260215165735740624"`

---

#### 4. 数据库设计
- ✅ `mall_payment` 表 - 支付记录表
- ✅ `mall_refund` 表 - 退款记录表
- ✅ 支付状态枚举（PENDING / SUCCESS / FAILED / CLOSED / REFUNDED）
- ✅ 支付方式枚举（MOCK / ALIPAY / WECHAT）

**表结构**：
```sql
-- 支付记录表
CREATE TABLE `mall_payment` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `payment_no` VARCHAR(50) UNIQUE NOT NULL,
    `order_no` VARCHAR(50) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `amount` DECIMAL(10,2) NOT NULL,
    `payment_method` VARCHAR(20) NOT NULL,
    `payment_status` VARCHAR(20) NOT NULL,
    `trade_no` VARCHAR(100),
    `code_url` VARCHAR(500),  -- 微信支付二维码链接
    `notify_time` DATETIME,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL
);

-- 退款记录表
CREATE TABLE `mall_refund` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `refund_no` VARCHAR(50) UNIQUE NOT NULL,
    `payment_no` VARCHAR(50) NOT NULL,
    `order_no` VARCHAR(50) NOT NULL,
    `amount` DECIMAL(10,2) NOT NULL,
    `refund_status` VARCHAR(20) NOT NULL,
    `refund_time` DATETIME,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL
);
```

---

#### 5. 错误码新增
- ✅ `40601` - 支付失败
- ✅ `40602` - 支付记录不存在
- ✅ `40603` - 支付关闭失败
- ✅ `40604` - 退款失败
- ✅ `40605` - 退款记录不存在
- ✅ `40606` - 支付已退款
- ✅ `40504` - 订单商品明细不存在

---

#### 6. 开发文档
- ✅ `PAYMENT_API.md` - 支付功能 API 文档
- ✅ `ALIPAY_DEPLOYMENT_CHECKLIST.md` - 支付宝部署检查清单
- ✅ `DEPLOYMENT_CONFIG_SUMMARY.md` - 部署配置总结
- ✅ 数据库迁移脚本（`apply_payment_schema.sql`）

文档位置：项目根目录

---

#### 7. SKU 多规格支持（2026-04）

- ✅ 商品规格维度表 `mall_product_spec`（颜色、尺寸等）
- ✅ 商品规格值表 `mall_product_spec_value`（红色、XL 等）
- ✅ 商品 SKU 表 `mall_sku`（规格组合 → 独立价格/库存/图片）
- ✅ 商品表新增 `has_sku` 字段区分 SKU/非SKU 商品
- ✅ 购物车支持 SKU（`mall_cart_item` 新增 `sku_id`，唯一约束更新为 `user+product+sku`）
- ✅ 订单明细支持 SKU（`mall_order_item` 新增 `sku_id`、`spec_desc`）
- ✅ 下单流程适配：有 SKU 商品扣减 `mall_sku.stock`，无 SKU 走 `mall_product.stock`
- ✅ 管理员 SKU 配置接口（全量保存/查询/删除）
- ✅ 商品详情接口返回规格树 + SKU 列表 + 价格区间
- ✅ 前端 SKU 配置编辑器组件 `SkuConfigEditor.vue`

**数据库迁移**：`db/V4_sku_support.sql`

#### 8. Bug 修复 — 掉单补偿库存恢复未区分 SKU（2026-04-05）

- ✅ 修复 `PaymentCheckConsumer` 掉单取消订单时不恢复 SKU 库存的 bug
- **根因**：掉单消费者对所有订单明细统一调用 `productMapper.increaseStock()`，未判断 `skuId`
- **影响**：有 SKU 的商品在延迟队列超时取消后，SKU 维度库存不恢复（永久扣减）
- **修复**：区分 `skuId > 0` 走 `skuMapper.increaseStock()`，否则走 `productMapper.increaseStock()`
- **文件**：`PaymentCheckConsumer.java`

---

## 八、开发计划

### 8.1 阶段划分

| 阶段 | 内容 | 预估时间 | 状态 |
|------|------|----------|------|
| **阶段1** | 项目初始化 + 数据库 + 基础架构 | 1天 | ✅ 已完成 |
| **阶段2** | 认证模块（注册/登录/JWT） | 1天 | ✅ 已完成 |
| **阶段3** | 用户模块 + 地址模块 | 0.5天 | ✅ 已完成 |
| **阶段4** | 分类模块 + 商品模块 | 1天 | ✅ 已完成 |
| **阶段5** | 购物车模块 | 0.5天 | ✅ 已完成 |
| **阶段6** | 订单模块 + 库存管理 | 1.5天 | ✅ 已完成 |
| **阶段7** | 支付模拟 + 支付宝 + 微信支付 + Stripe | 2天 | ✅ 已完成 |
| **阶段8** | 后台管理接口 | 1天 | ✅ 已完成 |
| **阶段9** | 测试 + 文档完善 | 1天 | ✅ 已完成 |
| **阶段10** | 数据库大表优化（索引、归档、缓存） | 1天 | ✅ 已完成 |
| **阶段11** | SKU 多规格支持（规格/SKU 表、购物车/订单/库存适配） | 2天 | ✅ 已完成 |

**总计：约 12 天**

### 7.2 阶段1详细任务

- [ ] 创建 Spring Boot 项目
- [ ] 配置 pom.xml 依赖
- [ ] 配置 application.yml
- [ ] 配置 MyBatis
- [ ] 配置 Swagger
- [ ] 创建数据库和表
- [ ] 实现统一响应格式 Result
- [ ] 实现全局异常处理

---

## 八、技术要点

### 8.1 JWT 认证流程

```
┌─────────┐     登录请求      ┌─────────┐
│  Client │ ───────────────► │  Server │
└─────────┘                  └────┬────┘
     │                            │
     │     返回 AccessToken       │ 验证用户名密码
     │ ◄──────────────────────────┤ 生成 JWT
     │                            │
     │     携带 Token 请求        │
     │ ───────────────────────►   │
     │    Authorization:          │ 验证 JWT
     │    Bearer <token>          │ 解析用户信息
     │                            │
     │     返回数据               │
     │ ◄──────────────────────────┤
```

### 8.2 下单流程

```
1. 用户提交订单
       │
       ▼
2. 校验购物车商品
       │
       ▼
3. 校验库存是否充足 ──否──► 返回库存不足错误
       │
      是
       │
       ▼
4. 扣减库存（事务）
       │
       ▼
5. 生成订单号
       │
       ▼
6. 创建订单主表记录
       │
       ▼
7. 创建订单明细记录
       │
       ▼
8. 清空已购买的购物车商品
       │
       ▼
9. 返回订单信息
```

### 8.3 取消订单流程

```
1. 用户/管理员/掉单消费者 请求取消
       │
       ▼
2. 校验订单状态 ──非UNPAID/PAID──► 返回无法取消
       │
    UNPAID 或 PAID
       │
       ├── PAID? ──是──► 调用退款接口
       │
       ▼
3. 遍历订单明细，恢复库存（事务内）
       │
       ├── skuId > 0 → skuMapper.increaseStock(skuId)
       │
       └── skuId = 0 → productMapper.increaseStock(productId)
       │                + Redis 库存恢复
       ▼
4. 更新订单状态为 CANCELLED
       │
       ▼
5. 返回成功
```

---

## 九、配置示例

### 9.1 application.yml

```yaml
server:
  port: 8080

spring:
  profiles:
    active: dev
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mall?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.mall.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

jwt:
  secret: your-256-bit-secret-key-here-make-it-long-enough
  expiration: 86400000  # 24小时

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

---

