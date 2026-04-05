package site.geekie.shop.shoppingmall.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import site.geekie.shop.shoppingmall.dto.ProductDTO;
import site.geekie.shop.shoppingmall.entity.CategoryDO;
import site.geekie.shop.shoppingmall.entity.ProductDO;
import site.geekie.shop.shoppingmall.mapper.CategoryMapper;
import site.geekie.shop.shoppingmall.vo.ProductVO;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品转换器
 * 负责 ProductDO 与 ProductVO 之间的转换
 * 包含关联查询优化逻辑，避免 N+1 问题
 */
@Mapper(componentModel = "spring")
public interface ProductConverter {

    /**
     * 将 ProductDO 转换为 ProductVO（基础映射）
     * 注意：categoryName、specs、skuList、minPrice、maxPrice 字段需后续填充
     *
     * @param product 商品实体
     * @return 商品VO（上述字段为null）
     */
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "specs", ignore = true)
    @Mapping(target = "skuList", ignore = true)
    @Mapping(target = "minPrice", ignore = true)
    @Mapping(target = "maxPrice", ignore = true)
    ProductVO toVO(ProductDO product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "salesCount", ignore = true)
    @Mapping(target = "hasSku", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductDO toDO(ProductDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "salesCount", ignore = true)
    @Mapping(target = "hasSku", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateDOFromDTO(ProductDTO dto, @MappingTarget ProductDO product);

    /**
     * 将 ProductDO 转换为 ProductVO 并填充分类名称
     * 适用于单个商品转换
     *
     * @param product 商品实体
     * @param categoryMapper 分类Mapper，用于查询分类信息
     * @return 商品VO（包含categoryName）
     */
    default ProductVO toVOWithCategory(ProductDO product, CategoryMapper categoryMapper) {
        if (product == null) {
            return null;
        }

        // 1. 先进行基础字段映射
        ProductVO vo = toVO(product);

        // 2. 填充分类名称
        if (product.getCategoryId() != null) {
            CategoryDO category = categoryMapper.findById(product.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        return vo;
    }

    /**
     * 批量将 ProductDO 转换为 ProductVO 并填充分类名称
     * 使用 IN 查询优化，避免 N+1 问题
     *
     * 性能优化策略：
     * 1. 收集所有唯一的 categoryId
     * 2. 批量查询所有分类（一次 SQL）
     * 3. 构建 Map<Long, CategoryDO> 缓存
     * 4. 遍历商品列表时从缓存中获取分类名称
     *
     * @param products 商品实体列表
     * @param categoryMapper 分类Mapper，用于批量查询分类信息
     * @return 商品VO列表（包含categoryName）
     */
    default List<ProductVO> toVOList(List<ProductDO> products, CategoryMapper categoryMapper) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 收集所有唯一的 categoryId
        List<Long> categoryIds = products.stream()
                .map(ProductDO::getCategoryId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        // 2. 批量查询所有分类，构建 Map 缓存（避免 N+1 查询问题）
        Map<Long, CategoryDO> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryMapper.findByIds(categoryIds).stream()
                        .collect(Collectors.toMap(CategoryDO::getId, c -> c));

        // 3. 转换商品列表并从缓存中填充分类名称
        return products.stream()
                .map(product -> {
                    ProductVO vo = toVO(product);

                    // 从缓存中获取分类名称
                    if (product.getCategoryId() != null) {
                        CategoryDO category = categoryMap.get(product.getCategoryId());
                        if (category != null) {
                            vo.setCategoryName(category.getName());
                        }
                    }

                    return vo;
                })
                .collect(Collectors.toList());
    }
}
