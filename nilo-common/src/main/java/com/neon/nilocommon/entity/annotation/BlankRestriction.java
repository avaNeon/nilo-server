package com.neon.nilocommon.entity.annotation;

import com.neon.nilocommon.validator.BlankRestrictionValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

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
