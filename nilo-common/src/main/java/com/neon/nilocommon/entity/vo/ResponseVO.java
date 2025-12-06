package com.neon.nilocommon.entity.vo;

import com.neon.nilocommon.entity.enums.ResponseCodeEnum;
import com.neon.nilocommon.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResponseVO<T>
{
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR = "error";

    private String status;
    private Integer code;
    private String info;
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
        responseVO.setCode(ResponseCodeEnum.SUCCESS.getCode());
        responseVO.setInfo(ResponseCodeEnum.SUCCESS.getMsg());
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
        vo.setCode(ResponseCodeEnum.UNKNOWN_ERROR.getCode());
        vo.setInfo(ResponseCodeEnum.UNKNOWN_ERROR.getMsg());
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
        if (e.getCode() == null) vo.setCode(ResponseCodeEnum.UNKNOWN_ERROR.getCode());
        else vo.setCode(e.getCode());
        vo.setInfo(e.getMessage());
        vo.setData(t);
        return vo;
    }
}
