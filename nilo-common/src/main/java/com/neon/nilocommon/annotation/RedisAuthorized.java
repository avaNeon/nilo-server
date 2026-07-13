package com.neon.nilocommon.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 微服务侧登录校验（仅 Redis，不回源 MySQL）。
 * <p>与 web 包 {@code Authorized} 区分，供 comment 等服务使用。</p>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisAuthorized
{
    boolean checkLogin() default true;
}
