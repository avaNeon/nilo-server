package com.neon.nilocommon.entity.vo;

import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Schema(name = "标准返回对象")
@Getter
@Setter
public class ResponseVO<T>
{
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR = "error";
    @Schema(name = "状态")
    private String status;
    @Schema(name = "响应码")
    private Integer code;
    @Schema(name = "响应信息")
    private String info;
    @Schema(name = "详细信息或数据")
    private T data;

    /**
     * 获取成功VO对象
     *
     * @param t   数据信息
     * @param <T> 数据信息类型
     * @return 成功VO对象
     */
    public static <T> ResponseVO <T> success(T t)
    {
        ResponseVO <T> responseVO = new ResponseVO <>();
        responseVO.setStatus(STATUS_SUCCESS);
        responseVO.setCode(ResponseCode.SUCCESS.getCode());
        responseVO.setInfo(ResponseCode.SUCCESS.getMsg());
        responseVO.setData(t);
        return responseVO;
    }

    /**
     * 创建未知异常VO对象
     *
     * @param t   数据信息
     * @param <T> 数据信息类型
     * @return 未知异常VO对象
     */
    public static <T> ResponseVO <T> error(T t)
    {
        ResponseVO <T> vo = new ResponseVO <>();
        vo.setStatus(STATUS_ERROR);
        vo.setCode(ResponseCode.UNKNOWN_ERROR.getCode());
        vo.setInfo(ResponseCode.UNKNOWN_ERROR.getMsg());
        vo.setData(t);
        return vo;
    }

    /**
     * 创建业务异常VO对象
     *
     * @param e   业务异常
     * @param t   数据信息
     * @param <T> 数据信息类型
     * @return 业务异常VO对象
     */
    public static <T> ResponseVO <T> error(BusinessException e, T t)
    {
        ResponseVO <T> vo = new ResponseVO <>();
        vo.setStatus(STATUS_ERROR);
        if (e.getCode() == null) vo.setCode(ResponseCode.UNKNOWN_ERROR.getCode());
        else vo.setCode(e.getCode());
        vo.setInfo(e.getMessage());
        vo.setData(t);
        return vo;
    }
}
