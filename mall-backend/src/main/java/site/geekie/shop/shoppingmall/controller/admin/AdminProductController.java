package site.geekie.shop.shoppingmall.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import site.geekie.shop.shoppingmall.common.PageResult;
import site.geekie.shop.shoppingmall.common.Result;
import site.geekie.shop.shoppingmall.dto.ProductDTO;
import site.geekie.shop.shoppingmall.dto.ProductSkuConfigDTO;
import site.geekie.shop.shoppingmall.vo.ProductVO;
import site.geekie.shop.shoppingmall.service.ProductService;
import site.geekie.shop.shoppingmall.service.SkuService;

/**
 * 管理员-商品管理控制器
 * 提供商品管理的REST API（仅管理员可访问）
 *
 * 基础路径：/api/v1/admin/products
 * 所有接口都需要ADMIN角色权限
 */

@Tag(name = "AdminProduct", description = "管理员商品管理接口")
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminProductController {

    private final ProductService productService;
    private final SkuService skuService;

    /**
     * 获取所有商品（管理员）
     * GET /api/v1/admin/products
     *
     * @return 商品列表
     */
    @Operation(summary = "获取所有商品（管理员）")
    @GetMapping
    public Result<PageResult<ProductVO>> getAllProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Integer statusInt = null;
        if ("ON_SALE".equals(status)) statusInt = 1;
        else if ("OFF_SALE".equals(status)) statusInt = 0;
        return Result.success(productService.getAllProducts(page, size, keyword, categoryId, statusInt, sortBy, sortDir));
    }

    /**
     * 获取商品详情（管理员）
     * GET /api/v1/admin/products/{id}
     *
     * @param id 商品ID
     * @return 商品详情
     */
    @Operation(summary = "获取商品详情（管理员）")
    @GetMapping("/{id}")
    public Result<ProductVO> getProductById(@PathVariable Long id) {
        ProductVO product = productService.getProductById(id);
        return Result.success(product);
    }

    /**
     * 新增商品
     * POST /api/v1/admin/products
     *
     * @param request 商品请求
     * @return 新增的商品信息
     */
    @Operation(summary = "新增商品")
    @PostMapping
    public Result<ProductVO> createProduct(@Valid @RequestBody ProductDTO request) {
        ProductVO product = productService.createProduct(request);
        return Result.success(product);
    }

    /**
     * 修改商品
     * PUT /api/v1/admin/products/{id}
     *
     * @param id 商品ID
     * @param request 商品请求
     * @return 修改后的商品信息
     */
    @Operation(summary = "修改商品")
    @PutMapping("/{id}")
    public Result<ProductVO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductDTO request) {
        ProductVO product = productService.updateProduct(id, request);
        return Result.success(product);
    }

    /**
     * 删除商品
     * DELETE /api/v1/admin/products/{id}
     *
     * @param id 商品ID
     * @return 操作结果
     */
    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success();
    }

    /**
     * 修改商品状态（上架/下架）
     * PUT /api/v1/admin/products/{id}/status
     *
     * @param id 商品ID
     * @param status 状态（0-下架，1-上架）
     * @return 操作结果
     */
    @Operation
    @PutMapping("/{id}/status")
    public Result<Void> updateProductStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        productService.updateProductStatus(id, status);
        return Result.success();
    }

    /**
     * 修改商品库存
     * PUT /api/v1/admin/products/{id}/stock
     *
     * @param id 商品ID
     * @param stock 新库存
     * @return 操作结果
     */
    @Operation
    @PutMapping("/{id}/stock")
    public Result<Void> updateProductStock(
            @PathVariable Long id,
            @RequestParam Integer stock) {
        productService.updateProductStock(id, stock);
        return Result.success();
    }

    /**
     * 保存商品SKU配置（全量替换）
     * PUT /api/v1/admin/products/{id}/sku-config
     *
     * @param id     商品ID
     * @param config SKU配置
     * @return 操作结果
     */
    @Operation(summary = "保存商品SKU配置")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/{id}/sku-config")
    public Result<Void> saveSkuConfig(
            @PathVariable Long id,
            @Valid @RequestBody ProductSkuConfigDTO config) {
        skuService.saveProductSkuConfig(id, config);
        return Result.success();
    }

    /**
     * 获取商品SKU配置
     * GET /api/v1/admin/products/{id}/sku-config
     *
     * @param id 商品ID
     * @return SKU配置
     */
    @Operation(summary = "获取商品SKU配置")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}/sku-config")
    public Result<ProductSkuConfigDTO> getSkuConfig(@PathVariable Long id) {
        return Result.success(skuService.getProductSkuConfig(id));
    }

    /**
     * 删除商品SKU配置
     * DELETE /api/v1/admin/products/{id}/sku-config
     *
     * @param id 商品ID
     * @return 操作结果
     */
    @Operation(summary = "删除商品SKU配置")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{id}/sku-config")
    public Result<Void> deleteSkuConfig(@PathVariable Long id) {
        skuService.deleteProductSkuConfig(id);
        return Result.success();
    }
}
