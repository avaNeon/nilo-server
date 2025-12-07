package com.neon.nilocommon.entity.validator;

import com.neon.nilocommon.entity.annotation.BlankRestriction;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * 校验Query对象是否参数全为空
 */
@Slf4j
public class BlankRestrictionValidator implements ConstraintValidator <BlankRestriction, Object>
{

    @Override
    public boolean isValid(Object query, ConstraintValidatorContext context)
    {
        if (query == null) return true; //交给@NotNull注解处理
        Class <?> realClass = query.getClass();
        for (Field field : realClass.getDeclaredFields()) // 只检查当前类属性，不检查父类属性
        {
            field.setAccessible(true);
            try
            {
                Object o = field.get(query);
                if (!isBlank(o)) return true; // 如果一个参数不为空，那么就是true
            }
            catch (IllegalAccessException e)
            {
                log.error(e.getMessage());
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    /**
     * 校验任意类型数据是否为空
     *
     * @param o 任意类型数据
     * @return true：为空 false：非空
     */
    private boolean isBlank(Object o)
    {
        if (o == null) return true;
        else if (o instanceof String s) return s.isBlank();
        else if (o instanceof Collection <?> c) return c.isEmpty();
        else if (o instanceof Map <?, ?> m) return m.isEmpty();
        else return false;
    }
}
