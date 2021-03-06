package com.jframework.licp.test.basetest;

import org.junit.Rule;
import org.junit.Test;
import com.jfire.test.rule.CustomRule;
import com.jfire.test.rule.MutiThreadTest;
import com.jfireframework.baseutil.collection.buffer.ByteBuf;
import com.jfireframework.baseutil.collection.buffer.HeapByteBufPool;
import com.jfireframework.licp.Licp;
import com.jframework.licp.test.basetest.data.Person;

public class MutiTest
{
    @Rule
    public CustomRule rule = new CustomRule();
    
    @Test
    @MutiThreadTest(repeatTimes = 30, threadNums = 10)
    public void test()
    {
        ByteBuf<?> buf = HeapByteBufPool.getInstance().get(100);
        Licp lbse = new Licp();
        Person person = new Person();
        lbse.serialize(person, buf);
        lbse.deserialize(buf, Person.class);
    }
}
