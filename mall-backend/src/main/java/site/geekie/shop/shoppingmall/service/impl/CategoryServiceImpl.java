package site.geekie.shop.shoppingmall.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.converter.CategoryConverter;
import site.geekie.shop.shoppingmall.dto.CategoryDTO;
import site.geekie.shop.shoppingmall.entity.CategoryDO;
import site.geekie.shop.shoppingmall.vo.CategoryVO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.CategoryMapper;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.service.CategoryService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 分类服务实现类
 * 实现商品分类的CRUD操作和树形结构构建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final String CACHE_TREE_KEY = "cache:category:tree";
    private static final String CACHE_ALL_KEY = "cache:category:all";
    private static final long CACHE_TTL_HOURS = 1;

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final CategoryConverter categoryConverter;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 支持 Java 8 时间类型（LocalDateTime）的 ObjectMapper。
     * 不注入 Spring 容器中的全局 ObjectMapper，避免影响其他组件的序列化配置。
     */
    private final ObjectMapper objectMapper = buildObjectMapper();

    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Override
    public List<CategoryVO> getAllCategories() {
        // 1. 尝试读取缓存
        try {
            String cached = stringRedisTemplate.opsForValue().get(CACHE_ALL_KEY);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<CategoryVO>>() {});
            }
        } catch (Exception e) {
            log.warn("读取分类全量缓存失败，降级查询数据库: {}", e.getMessage());
        }

        // 2. 缓存未命中，查询数据库
        List<CategoryDO> categories = categoryMapper.findAll();
        List<CategoryVO> result = categoryConverter.toVOList(categories);

        // 3. 批量查询每个分类的商品数量
        List<Long> categoryIds = result.stream()
                .map(CategoryVO::getId)
                .collect(Collectors.toList());
        Map<Long, Integer> countMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            List<Map<String, Object>> counts = productMapper.countGroupByCategoryIds(categoryIds);
            for (Map<String, Object> row : counts) {
                Long catId = ((Number) row.get("categoryId")).longValue();
                Integer cnt = ((Number) row.get("cnt")).intValue();
                countMap.put(catId, cnt);
            }
        }
        for (CategoryVO vo : result) {
            vo.setProductCount(countMap.getOrDefault(vo.getId(), 0));
        }

        // 4. 写入缓存
        try {
            String json = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(CACHE_ALL_KEY, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("写入分类全量缓存失败: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public List<CategoryVO> getCategoryTree() {
        // 1. 尝试读取缓存
        try {
            String cached = stringRedisTemplate.opsForValue().get(CACHE_TREE_KEY);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<CategoryVO>>() {});
            }
        } catch (Exception e) {
            log.warn("读取分类树缓存失败，降级查询数据库: {}", e.getMessage());
        }

        // 2. 缓存未命中，查询数据库并构建树
        List<CategoryDO> allCategories = categoryMapper.findAll();
        List<CategoryVO> allResponses = categoryConverter.toVOList(allCategories);
        List<CategoryVO> tree = buildTree(allResponses, 0L);

        // 3. 写入缓存
        try {
            String json = objectMapper.writeValueAsString(tree);
            stringRedisTemplate.opsForValue().set(CACHE_TREE_KEY, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("写入分类树缓存失败: {}", e.getMessage());
        }

        return tree;
    }

    @Override
    public List<CategoryVO> getCategoriesByParentId(Long parentId) {
        List<CategoryDO> categories = categoryMapper.findByParentId(parentId);
        return categoryConverter.toVOList(categories);
    }

    @Override
    public CategoryVO getCategoryById(Long id) {
        CategoryDO category = categoryMapper.findById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND);
        }
        return categoryConverter.toVO(category);
    }

    @Override
    @Transactional
    public CategoryVO addCategory(CategoryDTO request) {
        // 1. 验证父分类存在性（如果不是顶级分类）
        if (request.getParentId() > 0) {
            CategoryDO parentCategory = categoryMapper.findById(request.getParentId());
            if (parentCategory == null) {
                throw new BusinessException(ResultCode.INVALID_PARENT_CATEGORY);
            }

            // 验证层级关系：父分类level + 1 应该等于当前分类level
            if (parentCategory.getLevel() + 1 != request.getLevel()) {
                throw new BusinessException(ResultCode.INVALID_PARENT_CATEGORY);
            }

            // 验证最大层级：不超过3级
            if (request.getLevel() > 3) {
                throw new BusinessException(ResultCode.INVALID_PARENT_CATEGORY);
            }
        } else {
            // 顶级分类level必须为1
            if (request.getLevel() != 1) {
                throw new BusinessException(ResultCode.INVALID_PARENT_CATEGORY);
            }
        }

        // 2. 检查同级分类名称是否重复
        CategoryDO existingCategory = categoryMapper.findByNameAndParentId(
                request.getName(), request.getParentId());
        if (existingCategory != null) {
            throw new BusinessException(ResultCode.CATEGORY_NAME_DUPLICATE);
        }

        // 3. 创建分类
        CategoryDO category = categoryConverter.toDO(request);

        categoryMapper.insert(category);

        evictCategoryCache();

        return categoryConverter.toVO(category);
    }

    @Override
    @Transactional
    public CategoryVO updateCategory(Long id, CategoryDTO request) {
        // 1. 查询分类是否存在
        CategoryDO category = categoryMapper.findById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND);
        }

        // 2. 检查名称是否与同级其他分类重复
        CategoryDO existingCategory = categoryMapper.findByNameAndParentId(
                request.getName(), category.getParentId());
        if (existingCategory != null && !existingCategory.getId().equals(id)) {
            throw new BusinessException(ResultCode.CATEGORY_NAME_DUPLICATE);
        }

        // 3. 更新分类信息（不允许修改parentId和level）
        categoryConverter.updateDOFromDTO(request, category);

        categoryMapper.updateById(category);

        evictCategoryCache();

        return categoryConverter.toVO(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        // 1. 查询分类是否存在
        CategoryDO category = categoryMapper.findById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND);
        }

        // 2. 检查是否有子分类
        int childrenCount = categoryMapper.countByParentId(id);
        if (childrenCount > 0) {
            throw new BusinessException(ResultCode.CATEGORY_HAS_CHILDREN);
        }

        // 3. 检查是否有商品（待Product模块实现后补充）
        // TODO: 检查该分类下是否有商品

        // 4. 删除分类
        categoryMapper.deleteById(id);

        evictCategoryCache();
    }

    /**
     * 清除分类相关的全部缓存 key。
     * Redis 异常不影响业务流程。
     */
    private void evictCategoryCache() {
        try {
            stringRedisTemplate.delete(CACHE_TREE_KEY);
            stringRedisTemplate.delete(CACHE_ALL_KEY);
        } catch (Exception e) {
            log.warn("清除分类缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 构建分类树形结构
     *
     * @param allCategories 所有分类列表
     * @param parentId 父分类ID
     * @return 树形结构的分类列表
     */
    private List<CategoryVO> buildTree(List<CategoryVO> allCategories, Long parentId) {
        List<CategoryVO> tree = new ArrayList<>();

        for (CategoryVO category : allCategories) {
            if (category.getParentId().equals(parentId)) {
                // 递归查找子分类
                List<CategoryVO> children = buildTree(allCategories, category.getId());
                if (!children.isEmpty()) {
                    category.setChildren(children);
                }
                tree.add(category);
            }
        }

        return tree;
    }

}
