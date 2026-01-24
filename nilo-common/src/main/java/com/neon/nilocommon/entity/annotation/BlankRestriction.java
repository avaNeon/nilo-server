package com.neon.nilocommon.entity.annotation;

import com.neon.nilocommon.validator.BlankRestrictionValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 空白限制注解，标注了这个限制的类在前端传入时，不能所有属性都为空，至少有一个属性有值
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {BlankRestrictionValidator.class})
public @interface BlankRestriction
{
    String message() default "{BlankRestriction.message}";
    Class <?>[] groups() default {};
    Class <? extends Payload>[] payload() default {};
}
