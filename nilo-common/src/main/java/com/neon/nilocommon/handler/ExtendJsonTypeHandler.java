package com.neon.nilocommon.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neon.nilocommon.entity.po.userMessage.ExtendJson;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * {@link ExtendJson} 的DB层转换器 <hr/>
 * <p>将 user_message.extend_json 的 JSON 字符串映射为 DTO 使用的 ExtendJson 对象</p>
 */
@MappedTypes(ExtendJson.class)
@MappedJdbcTypes({JdbcType.VARCHAR, JdbcType.LONGVARCHAR})
public class ExtendJsonTypeHandler extends BaseTypeHandler <ExtendJson>
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private ExtendJson parse(String value) throws SQLException
    {
        if (value == null || value.isBlank())
        {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.startsWith("{"))
        {
            try
            {
                return OBJECT_MAPPER.readValue(trimmed, ExtendJson.class);
            }
            catch (JsonProcessingException e)
            {
                throw new SQLException("Deserialize ExtendJson failed", e);
            }
        }

        return new ExtendJson(value, null);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ExtendJson parameter, JdbcType jdbcType) throws SQLException
    {
        try
        {
            ps.setString(i, OBJECT_MAPPER.writeValueAsString(parameter));
        }
        catch (JsonProcessingException e)
        {
            throw new SQLException("Serialize ExtendJson failed", e);
        }
    }

    @Override
    public ExtendJson getNullableResult(ResultSet rs, String columnName) throws SQLException
    {
        return parse(rs.getString(columnName));
    }

    @Override
    public ExtendJson getNullableResult(ResultSet rs, int columnIndex) throws SQLException
    {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public ExtendJson getNullableResult(CallableStatement cs, int columnIndex) throws SQLException
    {
        return parse(cs.getString(columnIndex));
    }
}
