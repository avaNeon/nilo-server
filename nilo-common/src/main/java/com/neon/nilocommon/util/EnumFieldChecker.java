package com.neon.nilocommon.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility methods to inspect enum constants by arbitrary field name and value.
 * <p>
 * Example:
 * Optional<MyEnum> e = EnumFieldChecker.findByFieldValue(MyEnum.class, "code", 1);
 */
public final class EnumFieldChecker
{

    private EnumFieldChecker()
    {
    }

    /**
     * Check whether the given enum class contains a constant whose field named {@code fieldName}
     * equals the provided {@code expected} value.
     *
     * @param enumClass the enum class to search
     * @param fieldName the field name or property name to compare
     * @param expected  the value to compare against
     * @return true if a matching enum constant is found
     */
    public static <E extends Enum <E>> boolean containsFieldValue(Class <E> enumClass, String fieldName, Object expected)
    {
        return findByFieldValue(enumClass, fieldName, expected).isPresent();
    }

    /**
     * Find an enum constant by the given field name and expected value.
     * Returns an Optional containing the first matching constant, or empty if none found.
     */
    public static <E extends Enum <E>> Optional <E> findByFieldValue(Class <E> enumClass, String fieldName, Object expected)
    {
        Objects.requireNonNull(enumClass, "enumClass");
        Objects.requireNonNull(fieldName, "fieldName");

        if (!enumClass.isEnum())
        {
            throw new IllegalArgumentException("Provided class is not an enum: " + enumClass.getName());
        }

        E[] constants = enumClass.getEnumConstants();
        for (E c : constants)
        {
            Object val = null;
            // try getter first: getXxx()
            String getter = "get" + capitalize(fieldName);
            try
            {
                Method m = findMethod(enumClass, getter);
                if (m != null)
                {
                    m.setAccessible(true);
                    val = m.invoke(c);
                }
            }
            catch (IllegalAccessException | InvocationTargetException ignored)
            {
                // fallback to field access below
            }

            // if getter not found or invocation failed, try field access
            if (val == null)
            {
                try
                {
                    Field f = findField(enumClass, fieldName);
                    if (f != null)
                    {
                        f.setAccessible(true);
                        val = f.get(c);
                    }
                }
                catch (IllegalAccessException ignored)
                {
                    // ignore and continue
                }
            }

            if (equalsValue(val, expected))
            {
                return Optional.of(c);
            }
        }

        return Optional.empty();
    }

    private static boolean equalsValue(Object a, Object b)
    {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // numeric comparison: allow different numeric types to match by value
        if (a instanceof Number && b instanceof Number)
        {
            try
            {
                BigDecimal da = new BigDecimal(a.toString());
                BigDecimal db = new BigDecimal(b.toString());
                return da.compareTo(db) == 0;
            }
            catch (Exception ex)
            {
                // fallback to double compare
                double da = ((Number) a).doubleValue();
                double db = ((Number) b).doubleValue();
                return Double.compare(da, db) == 0;
            }
        }
        return Objects.equals(a, b);
    }

    private static String capitalize(String s)
    {
        if (s == null || s.isEmpty()) return s;
        if (s.length() == 1) return s.toUpperCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Method findMethod(Class <?> cls, String name)
    {
        Class <?> cur = cls;
        while (cur != null && cur != Object.class)
        {
            Method[] methods = cur.getDeclaredMethods();
            for (Method m : methods)
            {
                if (m.getName().equals(name) && m.getParameterCount() == 0)
                {
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Field findField(Class <?> cls, String name)
    {
        Class <?> cur = cls;
        while (cur != null && cur != Object.class)
        {
            try
            {
                Field f = cur.getDeclaredField(name);
                return f;
            }
            catch (NoSuchFieldException e)
            {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}


