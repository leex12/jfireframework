package com.jfireframework.context.bean.field.dependency.impl;

import java.lang.reflect.Field;
import java.util.Map;
import com.jfireframework.baseutil.StringUtil;
import com.jfireframework.context.bean.Bean;

public class DefaultBeanField extends AbstractDependencyField
{
    private final Bean   dependencyBean;
    private final String msg;
    
    public DefaultBeanField(Field field, Bean bean)
    {
        super(field);
        dependencyBean = bean;
        msg = StringUtil.format("属性{}.{}进行注入", field.getDeclaringClass(), field.getName());
    }
    
    @Override
    public void inject(Object src, Map<String, Object> beanInstanceMap)
    {
        logger.trace(msg);
        unsafe.putObject(src, offset, dependencyBean.getInstance(beanInstanceMap));
    }
}
