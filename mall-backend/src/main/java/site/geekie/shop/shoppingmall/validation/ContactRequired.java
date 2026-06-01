package site.geekie.shop.shoppingmall.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ContactRequiredValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContactRequired {
    String message() default "邮箱或手机号至少填写一项";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
