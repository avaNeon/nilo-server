package com.neon.nilocommon.util;

import com.neon.nilocommon.entity.query.BaseQuery;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Java实现仿MySQL过滤/排序数据通用方法
 */
@Slf4j
public class RedisQueryUtil
{

    /**
     * 通用过滤和排序方法
     *
     * @param dataList    原始数据列表
     * @param query       查询参数对象（继承BaseQuery）
     * @param entityClass 实体类Class
     * @param <T>         实体类型
     * @param <Q>         查询参数类型
     * @return 过滤排序后的列表
     */
    public static <T, Q extends BaseQuery> List <T> filterAndSort(List <T> dataList, Q query, Class <T> entityClass)
    {
        if (dataList == null || dataList.isEmpty())
        {
            return new ArrayList <>();
        }

        // 1. 先过滤
        List <T> filteredList = filter(dataList, query, entityClass);

        // 2. 再排序
        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty())
        {
            filteredList = sort(filteredList, query.getOrderBy(), entityClass);
        }

        return filteredList;
    }

    /**
     * 通用过滤方法
     *
     * @param dataList    原始数据列表
     * @param query       查询参数对象
     * @param entityClass 实体类Class
     * @param <T>         实体类型
     * @param <Q>         查询参数类型
     * @return 过滤后的列表
     */
    private static <T, Q extends BaseQuery> List <T> filter(List <T> dataList, Q query, Class <T> entityClass)
    {
        return dataList.stream().filter(entity ->
                                        {
                                            try
                                            {
                                                // 获取query的所有字段
                                                Field[] queryFields = getAllFields(query.getClass());

                                                for (Field queryField : queryFields)
                                                {
                                                    queryField.setAccessible(true);
                                                    Object queryValue = queryField.get(query);

                                                    // 跳过null值、BaseQuery的基础字段
                                                    if (queryValue == null || isBaseQueryField(queryField.getName()))
                                                    {
                                                        continue;
                                                    }

                                                    String fieldName = queryField.getName();

                                                    // 判断是否为模糊查询字段
                                                    if (fieldName.endsWith("Fuzzy"))
                                                    {
                                                        // 模糊匹配
                                                        String actualFieldName = fieldName.substring(0, fieldName.length() - 5);
                                                        if (!matchFuzzy(entity, actualFieldName, queryValue, entityClass))
                                                        {
                                                            return false;
                                                        }
                                                    }
                                                    else
                                                    {
                                                        // 精确匹配（排除对应的Fuzzy字段已存在的情况）
                                                        if (!hasFuzzyField(query.getClass(), fieldName))
                                                        {
                                                            if (!matchExact(entity, fieldName, queryValue, entityClass))
                                                            {
                                                                return false;
                                                            }
                                                        }
                                                    }
                                                }

                                                return true;
                                            }
                                            catch (Exception e)
                                            {
                                                log.error("过滤数据时发生异常", e);
                                                return true; // 异常时保留数据
                                            }
                                        }).collect(Collectors.toList());
    }

    /**
     * 精确匹配
     */
    private static <T> boolean matchExact(T entity, String fieldName, Object queryValue, Class <T> entityClass)
    {
        try
        {
            Object entityValue = getFieldValue(entity, fieldName, entityClass);

            if (entityValue == null)
            {
                return false;
            }

            return entityValue.equals(queryValue);
        }
        catch (Exception e)
        {
            log.warn("精确匹配字段 {} 失败: {}", fieldName, e.getMessage());
            return true;
        }
    }

    /**
     * 模糊匹配
     */
    private static <T> boolean matchFuzzy(T entity, String fieldName, Object queryValue, Class <T> entityClass)
    {
        try
        {
            Object entityValue = getFieldValue(entity, fieldName, entityClass);

            if (entityValue == null)
            {
                return false;
            }

            // 转为字符串进行contains匹配
            String entityStr = entityValue.toString();
            String queryStr = queryValue.toString();

            return entityStr.contains(queryStr);
        }
        catch (Exception e)
        {
            log.warn("模糊匹配字段 {} 失败: {}", fieldName, e.getMessage());
            return true;
        }
    }

    /**
     * 通用排序方法
     *
     * @param dataList    数据列表
     * @param orderBy     排序字符串，例如 "categoryId desc" 或 "sort asc, categoryId desc"
     * @param entityClass 实体类Class
     * @param <T>         实体类型
     * @return 排序后的列表
     */
    private static <T> List <T> sort(List <T> dataList, String orderBy, Class <T> entityClass)
    {
        if (dataList == null || dataList.isEmpty() || orderBy == null || orderBy.trim().isEmpty())
        {
            return dataList;
        }

        try
        {
            // 解析多个排序字段
            String[] sortFields = orderBy.split(",");
            Comparator <T> comparator = null;

            for (String sortField : sortFields)
            {
                sortField = sortField.trim();
                if (sortField.isEmpty())
                {
                    continue;
                }

                // 解析字段名和排序方向
                String[] parts = sortField.split("\\s+");
                String fieldName = convertToCamelCase(parts[0].trim());
                boolean isDesc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());

                // 构建单个字段的Comparator
                Comparator <T> fieldComparator = buildComparator(fieldName, isDesc, entityClass);

                // 链式组合多个Comparator
                if (comparator == null)
                {
                    comparator = fieldComparator;
                }
                else
                {
                    comparator = comparator.thenComparing(fieldComparator);
                }
            }

            if (comparator != null)
            {
                dataList.sort(comparator);
            }

            return dataList;
        }
        catch (Exception e)
        {
            log.warn("排序失败，orderBy: {}, 错误: {}", orderBy, e.getMessage());
            return dataList;
        }
    }

    /**
     * 构建字段的Comparator
     */
    private static <T> Comparator <T> buildComparator(String fieldName, boolean isDesc, Class <T> entityClass)
    {
        Comparator <T> comparator = (o1, o2) ->
        {
            try
            {
                Object value1 = getFieldValue(o1, fieldName, entityClass);
                Object value2 = getFieldValue(o2, fieldName, entityClass);

                // null值处理：null排在最后
                if (value1 == null && value2 == null)
                {
                    return 0;
                }
                if (value1 == null)
                {
                    return 1;
                }
                if (value2 == null)
                {
                    return -1;
                }

                // 比较
                if (value1 instanceof Comparable)
                {
                    return ((Comparable) value1).compareTo(value2);
                }
                else
                {
                    return value1.toString().compareTo(value2.toString());
                }
            }
            catch (Exception e)
            {
                log.warn("比较字段 {} 时出错: {}", fieldName, e.getMessage());
                return 0;
            }
        };

        return isDesc ? comparator.reversed() : comparator;
    }

    /**
     * 通过反射获取字段值（支持驼峰和下划线命名）
     */
    private static <T> Object getFieldValue(T entity, String fieldName, Class <T> entityClass) throws Exception
    {
        try
        {
            // 优先尝试getter方法
            String getterName = "get" + capitalize(fieldName);
            Method getter = entityClass.getMethod(getterName);
            return getter.invoke(entity);
        }
        catch (NoSuchMethodException e)
        {
            // 尝试is方法（布尔类型）
            try
            {
                String isGetterName = "is" + capitalize(fieldName);
                Method getter = entityClass.getMethod(isGetterName);
                return getter.invoke(entity);
            }
            catch (NoSuchMethodException e2)
            {
                // 直接访问字段
                Field field = findField(entityClass, fieldName);
                if (field != null)
                {
                    field.setAccessible(true);
                    return field.get(entity);
                }
                throw new NoSuchFieldException("字段不存在: " + fieldName);
            }
        }
    }

    /**
     * 在类及其父类中查找字段
     */
    private static Field findField(Class <?> clazz, String fieldName)
    {
        Class <?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class)
        {
            try
            {
                return currentClass.getDeclaredField(fieldName);
            }
            catch (NoSuchFieldException e)
            {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 获取类及其所有父类的字段
     */
    private static Field[] getAllFields(Class <?> clazz)
    {
        List <Field> fields = new ArrayList <>();
        Class <?> currentClass = clazz;

        while (currentClass != null && currentClass != Object.class)
        {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }

        return fields.toArray(new Field[0]);
    }

    /**
     * 判断是否为BaseQuery的基础字段
     */
    private static boolean isBaseQueryField(String fieldName)
    {
        return "pageCalculator".equals(fieldName) || "pageNo".equals(fieldName) || "pageSize".equals(fieldName) || "orderBy".equals(
                fieldName);
    }

    /**
     * 判断Query类中是否有对应的Fuzzy字段
     */
    private static boolean hasFuzzyField(Class <?> queryClass, String fieldName)
    {
        try
        {
            queryClass.getDeclaredField(fieldName + "Fuzzy");
            return true;
        }
        catch (NoSuchFieldException e)
        {
            return false;
        }
    }

    /**
     * 将下划线命名转换为驼峰命名
     * 例如：category_id -> categoryId
     */
    private static String convertToCamelCase(String fieldName)
    {
        if (fieldName == null || !fieldName.contains("_"))
        {
            return fieldName;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (char c : fieldName.toCharArray())
        {
            if (c == '_')
            {
                capitalizeNext = true;
            }
            else
            {
                result.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            }
        }

        return result.toString();
    }

    /**
     * 首字母大写
     */
    private static String capitalize(String str)
    {
        if (str == null || str.isEmpty())
        {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}

