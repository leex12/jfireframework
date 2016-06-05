package com.jfireframework.mvc.binder.field.impl;

import java.lang.reflect.Field;
import java.util.Set;

public class FloatField extends AbstractBinderField
{
    
    public FloatField(String prefix, Field field, Set<Class<?>> cycleSet)
    {
        super(prefix, field, cycleSet);
    }
    
    @Override
    protected void set(Object entity, String value)
    {
        unsafe.putFloat(entity, offset, Float.parseFloat(value));
    }
    
}
