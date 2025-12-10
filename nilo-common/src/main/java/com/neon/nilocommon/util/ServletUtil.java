package com.neon.nilocommon.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.concurrent.TimeUnit;

/**
 * 和Servlet API有关的方法
 */
public class ServletUtil
{
    /**
     * 获取用户IP
     *
     * @return 用户IP
     */
    public static String getClientIp(HttpServletRequest request)
    {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            // X-Forwarded-For 可能有多个 IP，如： client, proxy1, proxy2 ...
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            return ip;
        }

        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            return ip;
        }

        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            return ip;
        }

        ip = request.getHeader("HTTP_CLIENT_IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            return ip;
        }

        ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        {
            return ip;
        }

        return request.getRemoteAddr();
    }

    /**
     * 向Cookie中加入键-值对
     *
     * @param response HttpsServletResponse
     * @param key      键
     * @param value    值
     * @param time     时间长度（如果是负数代表会话级）
     * @param timeUnit 时间长度单位
     */
    public static void setCookie(HttpServletResponse response, String key, String value, int time, TimeUnit timeUnit)
    {
        Cookie cookie = new Cookie(key, value);
        if (time < 0) cookie.setMaxAge(-1); // 会话级
        else cookie.setMaxAge(timeUnit.toSeconds(time) > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeUnit.toSeconds(time));
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    /**
     * 删除Cookie中指定键
     *
     * @param request  HttpServletRequest
     * @param response HttpServletResponse
     * @param key      删除的键名
     */
    public static void removeCookie(HttpServletRequest request, HttpServletResponse response, String key)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return;
        for (Cookie cookie : cookies)
        {
            if (cookie.getName().equals(key))
            {
                cookie.setMaxAge(0);
                cookie.setPath("/");
                response.addCookie(cookie);
                break;
            }
        }
    }

    /**
     * 从Cookie中获取指定值
     *
     * @param request HttpServletRequest
     * @param key     键名
     * @return key所对应的值
     */
    public static String getFromCookie(HttpServletRequest request, String key)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies)
        {
            if (cookie.getName().equals(key))
            {
                return cookie.getValue();
            }
        }
        return null;
    }
}
