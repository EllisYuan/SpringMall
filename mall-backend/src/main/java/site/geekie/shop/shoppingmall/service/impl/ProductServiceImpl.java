package site.geekie.shop.shoppingmall.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.geekie.shop.shoppingmall.common.PageResult;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.converter.ProductConverter;
import site.geekie.shop.shoppingmall.dto.ProductDTO;
import site.geekie.shop.shoppingmall.entity.CategoryDO;
import site.geekie.shop.shoppingmall.entity.ProductDO;
import site.geekie.shop.shoppingmall.vo.ProductVO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.CategoryMapper;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.service.ProductService;
import site.geekie.shop.shoppingmall.util.ProductCacheService;
import site.geekie.shop.shoppingmall.util.StockRedisService;

import java.util.List;
import java.util.Map;

/**
 * 商品服务实现类
 * 实现商品的CRUD操作和库存管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    // 排序列白名单，防止 SQL 注入
    private static final Map<String, String> SORT_COLUMN_WHITELIST = Map.of(
            "id", "prod.id",
            "name", "prod.name",
            "categoryName", "cat.name",
            "price", "prod.price",
            "stock", "prod.stock",
            "createdAt", "prod.created_at",
            "sales", "prod.sales_count"
    );

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductConverter productConverter;
    private final StockRedisService stockRedisService;
    private final ProductCacheService productCacheService;

    @Override
    public PageResult<ProductVO> getAllProducts(int page, int size, String keyword, Long categoryId, Integer status, String sortBy, String sortDir) {
        String sortColumn = SORT_COLUMN_WHITELIST.getOrDefault(sortBy, "prod.id");
        String dir = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        PageHelper.startPage(page, size);
        List<ProductDO> products = productMapper.findAllWithFilter(keyword, categoryId, status, sortColumn, dir);
        PageInfo<ProductDO> pageInfo = new PageInfo<>(products);
        List<ProductVO> list = productConverter.toVOList(products, categoryMapper);
        return new PageResult<>(list, pageInfo.getTotal(), page, size);
    }

    @Override
    public List<ProductVO> getProductsByCategoryId(Long categoryId) {
        List<ProductDO> products = productMapper.findByCategoryId(categoryId);
        return productConverter.toVOList(products, categoryMapper);
    }

    @Override
    public PageResult<ProductVO> searchProducts(String keyword, int page, int size) {
        PageHelper.startPage(page, size);
        List<ProductDO> products = productMapper.searchByKeyword(keyword);
        PageInfo<ProductDO> pageInfo = new PageInfo<>(products);
        List<ProductVO> list = productConverter.toVOList(products, categoryMapper);
        return new PageResult<>(list, pageInfo.getTotal(), page, size);
    }

    @Override
    public ProductVO getProductById(Long id) {
        // 1. 先查缓存，Redis 异常时降级直接查 DB
        ProductDO product = null;
        try {
            product = productCacheService.getProduct(id);
        } catch (Exception e) {
            log.warn("查询商品缓存异常，降级查 DB - productId: {}", id, e);
        }

        if (product == null) {
            // 2. 缓存未命中，查 DB
            product = productMapper.findById(id);
            if (product == null) {
                // 缓存空值防穿透
                try {
                    productCacheService.putNull(id);
                } catch (Exception e) {
                    log.warn("写入空值缓存异常 - productId: {}", id, e);
                }
                throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
            }
            // 3. 写入缓存
            try {
                productCacheService.putProduct(product);
            } catch (Exception e) {
                log.warn("写入商品缓存异常 - productId: {}", id, e);
            }
        }

        return productConverter.toVOWithCategory(product, categoryMapper);
    }

    @Override
    @Transactional
    public ProductVO addProduct(ProductDTO request) {
        // 1. 验证分类是否存在
        CategoryDO category = categoryMapper.findById(request.getCategoryId());
        if (category == null) {
            throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND);
        }

        // 2. 创建商品
        ProductDO product = productConverter.toDO(request);

        productMapper.insert(product);
        // 同步 Redis 库存
        if (Integer.valueOf(1).equals(product.getStatus())) {
            try {
                stockRedisService.setStock(product.getId(), product.getStock());
            } catch (Exception e) {
                log.warn("新增商品同步 Redis 库存异常 - productId: {}", product.getId(), e);
            }
        }

        return productConverter.toVOWithCategory(product, categoryMapper);
    }

    @Override
    @Transactional
    public ProductVO updateProduct(Long id, ProductDTO request) {
        // 1. 查询商品是否存在
        ProductDO product = productMapper.findById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 2. 如果要修改分类，验证分类是否存在
        if (request.getCategoryId() != null && !request.getCategoryId().equals(product.getCategoryId())) {
            CategoryDO category = categoryMapper.findById(request.getCategoryId());
            if (category == null) {
                throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND);
            }
        }

        // 3. 更新商品信息
        productConverter.updateDOFromDTO(request, product);

        productMapper.updateById(product);
        // 同步 Redis 库存
        try {
            if (Integer.valueOf(1).equals(request.getStatus())) {
                stockRedisService.setStock(id, request.getStock());
            } else {
                stockRedisService.removeStock(id);
            }
        } catch (Exception e) {
            log.warn("更新商品同步 Redis 库存异常 - productId: {}", id, e);
        }
        // 清除商品缓存
        try {
            productCacheService.evictProduct(id);
        } catch (Exception e) {
            log.warn("清除商品缓存异常 - productId: {}", id, e);
        }

        return productConverter.toVOWithCategory(product, categoryMapper);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        // 1. 查询商品是否存在
        ProductDO product = productMapper.findById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 2. 删除商品
        productMapper.deleteById(id);
        // 清除 Redis 库存缓存
        try {
            stockRedisService.removeStock(id);
        } catch (Exception e) {
            log.warn("删除商品清除 Redis 库存异常 - productId: {}", id, e);
        }
        // 清除商品缓存
        try {
            productCacheService.evictProduct(id);
        } catch (Exception e) {
            log.warn("删除商品清除商品缓存异常 - productId: {}", id, e);
        }
    }

    @Override
    @Transactional
    public void decreaseStock(Long id, Integer quantity) {
        // 1. 查询商品是否存在
        ProductDO product = productMapper.findById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 2. 扣减库存（使用乐观锁）
        int rows = productMapper.decreaseStock(id, quantity);
        if (rows == 0) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK);
        }
    }

    @Override
    @Transactional
    public void increaseStock(Long id, Integer quantity) {
        // 1. 查询商品是否存在
        ProductDO product = productMapper.findById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 2. 增加库存
        productMapper.increaseStock(id, quantity);
    }


    // ===== 管理员方法实现 =====

    @Override
    @Transactional
    public ProductVO createProduct(ProductDTO request) {
        // 直接调用addProduct，保持一致性
        return addProduct(request);
    }

    @Override
    @Transactional
    public void updateProductStatus(Long id, Integer status) {
        // 1. 查询商品是否存在
        ProductDO product = productMapper.findById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 2. 验证状态值是否合法
        if (status != 0 && status != 1) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "状态值必须为0或1");
        }

        // 3. 更新状态
        ProductDO updateProduct = new ProductDO();
        updateProduct.setId(id);
        updateProduct.setStatus(status);
        productMapper.updateById(updateProduct);
        // 上架时加载库存到 Redis，下架时清除
        try {
            if (status == 1) {
                stockRedisService.loadStockIfAbsent(id);
            } else {
                stockRedisService.removeStock(id);
            }
        } catch (Exception e) {
            log.warn("更新商品状态同步 Redis 库存异常 - productId: {}", id, e);
        }
        // 清除商品缓存
        try {
            productCacheService.evictProduct(id);
        } catch (Exception e) {
            log.warn("更新商品状态清除商品缓存异常 - productId: {}", id, e);
        }
    }

    @Override
    @Transactional
    public void updateProductStock(Long id, Integer stock) {
        // 1. 查询商品是否存在
        ProductDO product = productMapper.findById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 2. 验证库存值是否合法
        if (stock < 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "库存不能为负数");
        }

        // 3. 更新库存
        ProductDO updateProduct = new ProductDO();
        updateProduct.setId(id);
        updateProduct.setStock(stock);
        productMapper.updateById(updateProduct);
        // 同步 Redis 库存
        try {
            stockRedisService.setStock(id, stock);
        } catch (Exception e) {
            log.warn("更新库存同步 Redis 异常 - productId: {}", id, e);
        }
        // 清除商品缓存
        try {
            productCacheService.evictProduct(id);
        } catch (Exception e) {
            log.warn("更新库存清除商品缓存异常 - productId: {}", id, e);
        }
    }
}
