package site.geekie.shop.shoppingmall.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.geekie.shop.shoppingmall.common.ResultCode;
import site.geekie.shop.shoppingmall.dto.ProductSkuConfigDTO;
import site.geekie.shop.shoppingmall.dto.ProductSpecDTO;
import site.geekie.shop.shoppingmall.dto.SkuDTO;
import site.geekie.shop.shoppingmall.dto.SpecValueDTO;
import site.geekie.shop.shoppingmall.entity.ProductDO;
import site.geekie.shop.shoppingmall.entity.ProductSpecDO;
import site.geekie.shop.shoppingmall.entity.ProductSpecValueDO;
import site.geekie.shop.shoppingmall.entity.SkuDO;
import site.geekie.shop.shoppingmall.exception.BusinessException;
import site.geekie.shop.shoppingmall.mapper.ProductMapper;
import site.geekie.shop.shoppingmall.mapper.ProductSpecMapper;
import site.geekie.shop.shoppingmall.mapper.ProductSpecValueMapper;
import site.geekie.shop.shoppingmall.mapper.SkuMapper;
import site.geekie.shop.shoppingmall.service.SkuService;
import site.geekie.shop.shoppingmall.util.ProductCacheService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * SKU 服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {

    private final ProductMapper productMapper;
    private final ProductSpecMapper productSpecMapper;
    private final ProductSpecValueMapper productSpecValueMapper;
    private final SkuMapper skuMapper;
    private final ProductCacheService productCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveProductSkuConfig(Long productId, ProductSkuConfigDTO config) {
        // 1. 校验商品存在
        ProductDO product = productMapper.findById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 2. 校验 SKU 配置合法性（纯内存，不触发任何 DB 操作）
        validateSkuConfig(config);

        // 3. 全量删除旧规格配置（删除顺序：spec_value -> spec -> sku）
        productSpecValueMapper.deleteByProductId(productId);
        productSpecMapper.deleteByProductId(productId);
        skuMapper.deleteByProductId(productId);

        // 4. 插入新规格维度（逐条插入以获取自增ID）
        List<ProductSpecDTO> specDTOList = config.getSpecs();
        // 结构：specIndex -> valueIndex -> specValueId
        List<List<Long>> specValueIdMatrix = new ArrayList<>();

        for (int si = 0; si < specDTOList.size(); si++) {
            ProductSpecDTO specDTO = specDTOList.get(si);
            ProductSpecDO specDO = new ProductSpecDO();
            specDO.setProductId(productId);
            specDO.setName(specDTO.getName());
            specDO.setSortOrder(specDTO.getSortOrder() != null ? specDTO.getSortOrder() : si);
            productSpecMapper.insert(specDO);

            // 插入该规格下的选项值
            List<ProductSpecValueDO> valueDOs = new ArrayList<>();
            List<SpecValueDTO> valueDTOs = specDTO.getValues();
            for (int vi = 0; vi < valueDTOs.size(); vi++) {
                SpecValueDTO valueDTO = valueDTOs.get(vi);
                ProductSpecValueDO valueDO = new ProductSpecValueDO();
                valueDO.setSpecId(specDO.getId());
                valueDO.setProductId(productId);
                valueDO.setValue(valueDTO.getValue());
                valueDO.setSortOrder(valueDTO.getSortOrder() != null ? valueDTO.getSortOrder() : vi);
                valueDOs.add(valueDO);
            }
            // 批量插入（MyBatis useGeneratedKeys 批量模式）
            productSpecValueMapper.batchInsert(valueDOs);

            // 记录本规格的所有 specValue ID
            List<Long> valueIds = valueDOs.stream()
                    .map(ProductSpecValueDO::getId)
                    .collect(Collectors.toList());
            specValueIdMatrix.add(valueIds);
        }

        // 5. 构建 SKU 列表（校验已前置完成，此处只做 ID 转换和 DO 构建）
        List<Long> flatSpecValueIds = specValueIdMatrix.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        List<SkuDO> skuDOs = config.getSkuList().stream()
                .map(dto -> buildSkuDO(productId, dto, flatSpecValueIds, specDTOList))
                .collect(Collectors.toList());

        if (!skuDOs.isEmpty()) {
            // 默认 SKU 唯一性兜底：最多保留一个 isDefault=1
            long defaultCount = skuDOs.stream().filter(s -> Integer.valueOf(1).equals(s.getIsDefault())).count();
            if (defaultCount > 1) {
                boolean found = false;
                for (SkuDO s : skuDOs) {
                    if (Integer.valueOf(1).equals(s.getIsDefault())) {
                        if (found) {
                            s.setIsDefault(0);
                        } else {
                            found = true;
                        }
                    }
                }
            }
            skuMapper.batchInsert(skuDOs);
        }

        // 6. 同步 product 的 price（最低SKU价）、stock（库存总和）、hasSku
        syncProductPriceAndStockInternal(productId, skuDOs);
        ProductDO update = new ProductDO();
        update.setId(productId);
        update.setHasSku(1);
        productMapper.updateById(update);

        // 7. 清除商品缓存
        productCacheService.evictProduct(productId);
    }

    @Override
    public ProductSkuConfigDTO getProductSkuConfig(Long productId) {
        // 1. 查询商品存在
        ProductDO product = productMapper.findById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 2. 查询规格维度
        List<ProductSpecDO> specs = productSpecMapper.findByProductId(productId);

        // 3. 查询所有规格值（按商品ID批量查）
        List<ProductSpecValueDO> allSpecValues = productSpecValueMapper.findByProductId(productId);
        Map<Long, List<ProductSpecValueDO>> valuesBySpecId = allSpecValues.stream()
                .collect(Collectors.groupingBy(ProductSpecValueDO::getSpecId));

        // 4. 组装 DTO
        List<ProductSpecDTO> specDTOs = specs.stream().map(spec -> {
            ProductSpecDTO dto = new ProductSpecDTO();
            dto.setName(spec.getName());
            dto.setSortOrder(spec.getSortOrder());
            List<SpecValueDTO> valueDTOs = valuesBySpecId.getOrDefault(spec.getId(), List.of()).stream()
                    .map(v -> {
                        SpecValueDTO vDto = new SpecValueDTO();
                        vDto.setValue(v.getValue());
                        vDto.setSortOrder(v.getSortOrder());
                        return vDto;
                    }).collect(Collectors.toList());
            dto.setValues(valueDTOs);
            return dto;
        }).collect(Collectors.toList());

        // 5. 查询 SKU 列表
        List<SkuDO> skuList = skuMapper.findByProductId(productId);
        // 建立 specValueId -> 扁平序号 反向映射
        List<ProductSpecValueDO> sortedSpecValues = allSpecValues.stream()
                .sorted(Comparator.comparingLong(ProductSpecValueDO::getSpecId)
                        .thenComparingInt(v -> v.getSortOrder() != null ? v.getSortOrder() : 0)
                        .thenComparingLong(ProductSpecValueDO::getId))
                .collect(Collectors.toList());
        Map<Long, Long> idToFlatIndex = new java.util.LinkedHashMap<>();
        for (int i = 0; i < sortedSpecValues.size(); i++) {
            idToFlatIndex.put(sortedSpecValues.get(i).getId(), (long) i);
        }

        List<SkuDTO> skuDTOs = skuList.stream().map(sku -> {
            SkuDTO dto = new SkuDTO();
            dto.setSkuCode(sku.getSkuCode());
            dto.setPrice(sku.getPrice());
            dto.setStock(sku.getStock());
            dto.setImage(sku.getImage());
            // 将 spec_value_ids 字符串转换为扁平序号列表
            List<Long> flatIndices = Optional.ofNullable(sku.getSpecValueIds())
                    .filter(s -> !s.isEmpty())
                    .map(s -> Arrays.stream(s.split(","))
                            .map(String::trim)
                            .map(Long::parseLong)
                            .map(id -> idToFlatIndex.getOrDefault(id, id))
                            .collect(Collectors.<Long>toList()))
                    .orElseGet(ArrayList::new);
            dto.setSpecValueIds(flatIndices);
            dto.setIsDefault(Integer.valueOf(1).equals(sku.getIsDefault()));
            return dto;
        }).collect(Collectors.toList());

        ProductSkuConfigDTO result = new ProductSkuConfigDTO();
        result.setSpecs(specDTOs);
        result.setSkuList(skuDTOs);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProductSkuConfig(Long productId) {
        // 1. 校验商品存在
        ProductDO product = productMapper.findById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 2. 删除所有规格和SKU数据
        productSpecValueMapper.deleteByProductId(productId);
        productSpecMapper.deleteByProductId(productId);
        skuMapper.deleteByProductId(productId);

        // 3. 更新 product.hasSku = 0
        ProductDO update = new ProductDO();
        update.setId(productId);
        update.setHasSku(0);
        productMapper.updateById(update);

        // 4. 清除商品缓存
        productCacheService.evictProduct(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncProductPriceAndStock(Long productId) {
        List<SkuDO> skuList = skuMapper.findByProductId(productId);
        syncProductPriceAndStockInternal(productId, skuList);
    }

    /**
     * 校验 SKU 配置合法性（纯内存操作，不依赖 DB 自增 ID）
     */
    private void validateSkuConfig(ProductSkuConfigDTO config) {
        List<ProductSpecDTO> specDTOList = config.getSpecs();
        int specCount = specDTOList.size();

        // 计算扁平 specValue 总数
        int flatSize = specDTOList.stream()
                .mapToInt(s -> s.getValues().size())
                .sum();

        // 构建扁平序号 → spec 维度索引
        List<Integer> flatToSpecIndex = IntStream.range(0, specCount)
                .boxed()
                .flatMap(si -> specDTOList.get(si).getValues().stream().map(v -> si))
                .collect(Collectors.toList());

        for (SkuDTO skuDTO : config.getSkuList()) {
            List<Long> flatIndices = skuDTO.getSpecValueIds();
            // 校验1: specValueIds 数量 == spec 维度数量
            if (flatIndices == null || flatIndices.size() != specCount) {
                throw new BusinessException(ResultCode.SKU_CONFIG_INVALID);
            }
            Set<Long> seenIndices = new HashSet<>();
            Set<Integer> seenDimensions = new HashSet<>();
            for (Long idx : flatIndices) {
                // 校验2: 索引非空、不越界
                if (idx == null || idx < 0 || idx >= flatSize) {
                    throw new BusinessException(ResultCode.SKU_CONFIG_INVALID);
                }
                // 校验3: 无重复
                if (!seenIndices.add(idx)) {
                    throw new BusinessException(ResultCode.SKU_CONFIG_INVALID);
                }
                // 校验4: 每个 specValueId 来自不同维度
                if (!seenDimensions.add(flatToSpecIndex.get(idx.intValue()))) {
                    throw new BusinessException(ResultCode.SKU_CONFIG_INVALID);
                }
            }
        }
    }

    /**
     * 构建单个 SkuDO（校验已前置完成，此处只做 ID 转换）
     */
    private SkuDO buildSkuDO(Long productId, SkuDTO skuDTO, List<Long> flatSpecValueIds, List<ProductSpecDTO> specDTOList) {
        List<Long> flatIndices = skuDTO.getSpecValueIds();

        // 将扁平序号转换为实际 specValueId，并升序排列
        List<Long> actualSpecValueIds = flatIndices.stream()
                .map(idx -> flatSpecValueIds.get(idx.intValue()))
                .sorted()
                .collect(Collectors.toList());

        String specValueIdsStr = actualSpecValueIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String specDesc = buildSpecDesc(flatIndices, specDTOList);

        SkuDO skuDO = new SkuDO();
        skuDO.setProductId(productId);
        skuDO.setSkuCode(skuDTO.getSkuCode());
        skuDO.setSpecValueIds(specValueIdsStr);
        skuDO.setSpecDesc(specDesc);
        skuDO.setPrice(skuDTO.getPrice());
        skuDO.setStock(skuDTO.getStock());
        skuDO.setImage(skuDTO.getImage());
        skuDO.setStatus(1);
        skuDO.setIsDefault(Boolean.TRUE.equals(skuDTO.getIsDefault()) ? 1 : 0);
        return skuDO;
    }

    /**
     * 根据扁平序号构建规格描述字符串（如"红色,XL"）
     */
    private String buildSpecDesc(List<Long> flatIndices, List<ProductSpecDTO> specDTOList) {
        List<int[]> positions = IntStream.range(0, specDTOList.size())
                .boxed()
                .flatMap(si -> IntStream.range(0, specDTOList.get(si).getValues().size())
                        .mapToObj(vi -> new int[]{si, vi}))
                .collect(Collectors.toList());

        return flatIndices.stream()
                .map(Long::intValue)
                .filter(i -> i >= 0 && i < positions.size())
                .map(i -> specDTOList.get(positions.get(i)[0]).getValues().get(positions.get(i)[1]).getValue())
                .collect(Collectors.joining(","));
    }

    /**
     * 内部方法：根据 SKU 列表同步 product 的最低价和库存总和
     */
    private void syncProductPriceAndStockInternal(Long productId, List<SkuDO> skuList) {
        if (skuList == null || skuList.isEmpty()) {
            return;
        }

        BigDecimal minPrice = skuList.stream()
                .map(SkuDO::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        int totalStock = skuList.stream()
                .mapToInt(SkuDO::getStock)
                .sum();

        ProductDO update = new ProductDO();
        update.setId(productId);
        update.setPrice(minPrice);
        update.setStock(totalStock);
        productMapper.updateById(update);
    }
}
