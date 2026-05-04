package com.lytboot.framework.aspectj;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.lytboot.common.enums.DataSourceType;
import com.lytboot.framework.datasource.DynamicDataSourceContextHolder;

/**
 * 模块级数据源路由切面
 *
 * 对 system、generator、quartz 模块统一路由到 SLAVE 数据源，
 * 避免在每个 ServiceImpl 上手动加 @DataSource 注解。
 * 若方法/类上已有 @DataSource 注解（由 DataSourceAspect 处理），则本切面不覆盖。
 *
 * @author lytboot
 */
@Aspect
@Order(2)
@Component
public class ModuleDataSourceAspect
{
    /**
     * 切点：system、generator、quartz 模块下的所有方法
     */
    @Pointcut("execution(* com.lytboot.system..*.*(..)) || execution(* com.lytboot.generator..*.*(..)) || execution(* com.lytboot.quartz..*.*(..))")
    public void modulePointCut()
    {
    }

    @Around("modulePointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable
    {
        // 如果已有数据源设置（由 @DataSource 注解触发），则不覆盖
        if (DynamicDataSourceContextHolder.getDataSourceType() != null)
        {
            return point.proceed();
        }
        DynamicDataSourceContextHolder.setDataSourceType(DataSourceType.SLAVE.name());
        try
        {
            return point.proceed();
        }
        finally
        {
            DynamicDataSourceContextHolder.clearDataSourceType();
        }
    }
}
