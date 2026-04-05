package site.geekie.shop.shoppingmall.common;

import lombok.Getter;

/**
 * 响应状态码枚举
 * 定义系统中所有可能的响应状态码和对应的消息
 *
 * 状态码规则：
 * - 200: 成功
 * - 400-499: 客户端错误
 * - 500-599: 服务端错误
 * - 40001-40099: 用户相关错误
 * - 40101-40199: 商品相关错误
 * - 40201-40299: 分类相关错误
 * - 40301-40399: 购物车相关错误
 * - 40401-40499: 地址相关错误
 * - 40501-40599: 订单相关错误
 * - 40601-40699: 支付相关错误
 * - 40701-40799: 认证相关错误
 */
@Getter
public enum ResultCode {

    // ========== 通用状态码 ==========
    //成功
    SUCCESS(200, "success"),

    //错误请求
    BAD_REQUEST(400, "Bad request"),

    //无效的参数
    INVALID_PARAMETER(400, "Invalid parameter"),

    //未授权
    UNAUTHORIZED(401, "Unauthorized"),

    //禁止访问
    FORBIDDEN(403, "Forbidden"),

    //资源未找到
    NOT_FOUND(404, "Not found"),

    //服务器内部错误
    INTERNAL_SERVER_ERROR(500, "Internal server error"),

    // ========== 用户相关错误码 (40001-40099) ==========
    //用户名已存在
    USERNAME_ALREADY_EXISTS(40001, "Username already exists"),

    //邮箱已存在
    EMAIL_ALREADY_EXISTS(40002, "Email already exists"),

    //手机号已存在
    PHONE_ALREADY_EXISTS(40003, "Phone already exists"),

    //用户不存在
    USER_NOT_FOUND(40004, "User not found"),

    //用户名或密码错误
    INVALID_CREDENTIALS(40005, "Invalid username or password"),

    //账户已被禁用
    ACCOUNT_DISABLED(40006, "Account is disabled"),

    // ========== 商品相关错误码 (40101-40199) ==========
    //商品不存在
    PRODUCT_NOT_FOUND(40101, "Product not found"),

    //商品已售罄
    PRODUCT_OUT_OF_STOCK(40102, "Product is out of stock"),

    //库存不足
    INSUFFICIENT_STOCK(40103, "Insufficient stock"),

    //商品已下架
    PRODUCT_UNAVAILABLE(40104, "Product is unavailable"),

    // SKU 相关错误码
    SKU_NOT_FOUND(40151, "SKU not found"),
    SKU_OUT_OF_STOCK(40152, "SKU is out of stock"),
    SKU_REQUIRED(40153, "SKU is required for this product"),
    SKU_CONFIG_INVALID(40154, "SKU configuration is invalid"),

    // ========== 分类相关错误码 (40201-40299) ==========
    //分类不存在
    CATEGORY_NOT_FOUND(40201, "Category not found"),

    //分类有子分类，不能删除
    CATEGORY_HAS_CHILDREN(40202, "Category has children, cannot be deleted"),

    //分类下有商品，不能删除
    CATEGORY_HAS_PRODUCTS(40203, "Category has products, cannot be deleted"),

    //无效的父分类
    INVALID_PARENT_CATEGORY(40204, "Invalid parent category"),

    //分类名称重复
    CATEGORY_NAME_DUPLICATE(40205, "Category name already exists"),

    // ========== 购物车相关错误码 (40301-40399) ==========
    //购物车项不存在
    CART_ITEM_NOT_FOUND(40301, "Cart item not found"),

    //购物车为空
    CART_IS_EMPTY(40302, "Cart is empty"),

    //购物车中无已选中商品
    NO_CHECKED_CART_ITEMS(40303, "No checked items in cart"),

    // ========== 地址相关错误码 (40401-40499) ==========
    //地址不存在
    ADDRESS_NOT_FOUND(40401, "Address not found"),

    // ========== 订单相关错误码 (40501-40599) ==========
    //订单不存在
    ORDER_NOT_FOUND(40501, "Order not found"),

    //无效的订单状态
    INVALID_ORDER_STATUS(40502, "Invalid order status"),

    //订单无法取消
    ORDER_CANNOT_BE_CANCELLED(40503, "Order cannot be cancelled"),

    //订单商品明细不存在
    ORDER_ITEM_NOT_FOUND(40504, "Order item not found"),

    //下单操作过于频繁
    ORDER_CREATE_TOO_FREQUENT(40505, "Order creation too frequent, please try again later"),

    // ========== 支付相关错误码 (40601-40699) ==========
    //支付失败
    PAYMENT_FAILED(40601, "Payment failed"),

    //支付记录不存在
    PAYMENT_NOT_FOUND(40602, "Payment not found"),

    //支付关闭失败
    PAYMENT_CLOSE_FAILED(40603, "Payment close failed"),

    //退款失败
    REFUND_FAILED(40604, "Refund failed"),

    //退款记录不存在
    REFUND_NOT_FOUND(40605, "Refund record not found"),

    //支付已退款
    PAYMENT_ALREADY_REFUNDED(40606, "Payment already refunded"),

    //支付验证失败
    PAYMENT_VERIFY_FAILED(40607, "Payment verification failed"),

    //无效的支付状态（非法状态转换）
    INVALID_PAYMENT_STATUS(40608, "Invalid payment status transition"),

    //支付创建锁获取失败（并发冲突）
    PAYMENT_LOCK_FAILED(40609, "Failed to acquire payment lock, please try again later"),

    //该订单已通过其他方式支付成功
    PAYMENT_ALREADY_PAID_BY_OTHER_METHOD(40610, "Order has already been paid via another payment method"),

    // ========== 限流相关错误码 ==========
    //请求过于频繁
    RATE_LIMIT_EXCEEDED(42900, "Too many requests, please try again later"),

    // ========== 认证相关错误码 (40701-40799) ==========
    //无效的Token
    INVALID_TOKEN(40701, "Invalid token"),

    //Token已过期
    TOKEN_EXPIRED(40702, "Token expired");

    // 状态码
    private final int code;

    // 状态消息
    private final String message;

    /**
     * 构造函数
     *
     * @param code 状态码
     * @param message 状态消息
     */
    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
