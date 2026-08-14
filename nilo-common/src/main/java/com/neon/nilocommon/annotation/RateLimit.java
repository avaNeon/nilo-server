package com.neon.nilocommon.annotation;

import com.neon.nilocommon.entity.enums.RateLimitType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit
{
    RateLimitType by() default RateLimitType.IP;
}
