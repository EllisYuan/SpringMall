-- ========================================
-- V2: 组合索引优化
-- 执行方式: 低峰期手动执行
-- 回滚方案: 依次 DROP INDEX
--   ALTER TABLE mall_order DROP INDEX idx_user_id_status;
--   ALTER TABLE mall_order DROP INDEX idx_user_id_created_at;
--   ALTER TABLE mall_order_item DROP INDEX idx_order_id_product_id;
--   ALTER TABLE mall_product DROP INDEX idx_name_prefix;
-- ========================================

-- 优化 findByUserIdAndStatus (当前 user_id 和 status 是独立索引)
ALTER TABLE mall_order ADD INDEX idx_user_id_status (user_id, status);

-- 优化 findByUserId ORDER BY created_at DESC (覆盖索引，避免 filesort)
ALTER TABLE mall_order ADD INDEX idx_user_id_created_at (user_id, created_at);

-- 优化订单明细关联查询
ALTER TABLE mall_order_item ADD INDEX idx_order_id_product_id (order_id, product_id);

-- 商品名称前缀索引 (优化 LIKE 'keyword%' 前缀匹配场景)
ALTER TABLE mall_product ADD INDEX idx_name_prefix (name(50));

-- ========================================
-- ngram 全文索引（支持中文 2 字符分词）
-- 需要 MySQL 配置 ngram_token_size=2
-- 回滚方案:
--   ALTER TABLE mall_product DROP INDEX ft_name_subtitle;
-- ========================================
ALTER TABLE mall_product ADD FULLTEXT INDEX ft_name_subtitle (name, subtitle) WITH PARSER ngram;
