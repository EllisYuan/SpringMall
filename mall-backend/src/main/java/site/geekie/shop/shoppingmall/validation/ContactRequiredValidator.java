package site.geekie.shop.shoppingmall.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import site.geekie.shop.shoppingmall.dto.RegisterDTO;

public class ContactRequiredValidator implements ConstraintValidator<ContactRequired, RegisterDTO> {
    @Override
    public boolean isValid(RegisterDTO dto, ConstraintValidatorContext context) {
        if (dto == null) return true;
        boolean hasEmail = dto.getEmail() != null && !dto.getEmail().isBlank();
        boolean hasPhone = dto.getPhone() != null && !dto.getPhone().isBlank();
        return hasEmail || hasPhone;
    }
}
