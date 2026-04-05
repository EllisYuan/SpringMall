package site.geekie.shop.shoppingmall.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemVO {
    private Long id;
    private Long userId;
    private Long productId;
    private Long skuId;
    private String specDesc;
    private String skuImage;
    private String productName;
    private String productSubtitle;
    private String productImage;
    private BigDecimal productPrice;
    private Integer productStock;
    private Integer quantity;
    private Integer checked;
    private BigDecimal subtotal;
    private LocalDateTime createdAt;

    public CartItemVO(Long id, Long userId, Long productId, Integer quantity,
                      Integer checked, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.checked = checked;
        this.createdAt = createdAt;
    }
}
