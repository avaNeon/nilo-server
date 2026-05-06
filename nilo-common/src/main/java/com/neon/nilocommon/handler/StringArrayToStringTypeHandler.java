package com.neon.nilocommon.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 字符串数组类型处理器
 * 数据库存储格式: "tag1,tag2,tag3"
 * Java对象格式: String[]{"tag1", "tag2", "tag3"}
 */
@MappedTypes(String[].class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class StringArrayToStringTypeHandler extends BaseTypeHandler <String[]>
{

    private static final String SEPARATOR = ",";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String[] parameter, JdbcType jdbcType) throws SQLException
    {
        // 将String数组转换为逗号分隔的字符串存入数据库
        if (parameter == null || parameter.length == 0)
        {
            ps.setString(i, null);
        }
        else
        {
            ps.setString(i, String.join(SEPARATOR, parameter));
        }
    }

    @Override
    public String[] getNullableResult(ResultSet rs, String columnName) throws SQLException
    {
        String value = rs.getString(columnName);
        return convertToArray(value);
    }

    @Override
    public String[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException
    {
        String value = rs.getString(columnIndex);
        return convertToArray(value);
    }

    @Override
    public String[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException
    {
        String value = cs.getString(columnIndex);
        return convertToArray(value);
    }

    /**
     * 将逗号分隔的字符串转换为String数组
     *
     * @param value 数据库中的字符串值
     * @return String数组
     */
    private String[] convertToArray(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return new String[0];
        }
        // 按逗号分割并去除每个元素的空白
        String[] tags = value.split(SEPARATOR);
        for (int i = 0 ; i < tags.length ; i++)
        {
            tags[i] = tags[i].trim();
        }
        return tags;
    }
}
