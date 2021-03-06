package com.jfireframework.codejson.function.impl.write.wrapper;

import com.jfireframework.baseutil.collection.StringCache;
import com.jfireframework.codejson.function.impl.write.WriterAdapter;
import com.jfireframework.codejson.tracker.Tracker;

public class StringWriter extends WriterAdapter implements WrapperWriter
{
    
    public void write(Object field, StringCache cache, Object entity, Tracker tracker)
    {
//        String value = (String) field;
//        value = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
        cache.append('\"').append((String) field).append('\"');
    }
    
}
