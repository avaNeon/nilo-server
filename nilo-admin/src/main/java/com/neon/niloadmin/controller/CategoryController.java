package com.neon.niloadmin.controller;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "分类管理")
@RequestMapping(path = "/category")
@RestController
public class CategoryController
{
    /**
     * 测试接口<hr/>
     * 尽管请求头中键名首字母变成大写了，但是请求头的键名是大小写不敏感的
     */
    @Operation(summary = "测试接口")
    @GetMapping(path = "/test")
    ResponseVO <Boolean> test(@RequestHeader(name = Constants.COOKIE_TOKEN_ADMIN_KEY) String token)
    {
        return ResponseVO.success(true);
    }
}
