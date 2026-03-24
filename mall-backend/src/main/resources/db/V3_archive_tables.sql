-- ========================================
-- V3: 冷热数据分离 — 归档表
-- 执行方式: 低峰期手动执行
-- 回滚方案: DROP TABLE
--   DROP TABLE IF EXISTS mall_order_archive;
--   DROP TABLE IF EXISTS mall_order_item_archive;
--   DROP TABLE IF EXISTS mall_payment_archive;
--   DROP TABLE IF EXISTS mall_refund_archive;
-- ========================================

-- 归档表结构与主表完全一致
CREATE TABLE mall_order_archive LIKE mall_order;
CREATE TABLE mall_order_item_archive LIKE mall_order_item;
CREATE TABLE mall_payment_archive LIKE mall_payment;
CREATE TABLE mall_refund_archive LIKE mall_refund;

-- 归档表额外索引（针对历史查询场景）
ALTER TABLE mall_order_archive ADD INDEX idx_archive_user_created (user_id, created_at);
ALTER TABLE mall_order_archive ADD INDEX idx_archive_order_no (order_no);
