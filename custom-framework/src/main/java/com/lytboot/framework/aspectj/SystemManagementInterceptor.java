package com.lytboot.framework.aspectj;

import javax.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.lytboot.common.core.domain.AjaxResult;

/**
 * 系统管理接口写保护切面
 *
 * 客服系统独立化后，system、monitor 等模块的数据来自从库（另一套若依系统）。
 * 本切面对系统管理类 Controller 的写操作进行拦截，防止误改从库数据。
 *
 * @author lytboot
 */
@Aspect
@Component
public class SystemManagementInterceptor
{
    /**
     * 切点：system 和 monitor 包下的所有 Controller 方法，
     * 排除 SysLoginController、SysIndexController（登录和首页查询允许）
     */
    @Pointcut("execution(* com.lytboot.web.controller.system.*.*(..)) || execution(* com.lytboot.web.controller.monitor.*.*(..))")
    public void systemControllerPointCut()
    {
    }

    @Around("systemControllerPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable
    {
        String className = point.getTarget().getClass().getSimpleName();
        // 登录、首页、个人资料相关 Controller 允许通过
        if ("SysLoginController".equals(className)
                || "SysIndexController".equals(className)
                || "SysProfileController".equals(className)
                || "SysRegisterController".equals(className))
        {
            return point.proceed();
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null)
        {
            HttpServletRequest request = attributes.getRequest();
            String method = request.getMethod();
            // 拦截所有非 GET/HEAD/OPTIONS 的写操作
            if (!"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method))
            {
                return AjaxResult.error("系统数据请在管理后台维护");
            }
        }
        return point.proceed();
    }
}
