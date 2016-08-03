package com.jfireframework.context.test.function.event;

import javax.annotation.Resource;
import com.jfireframework.context.ContextInitFinish;
import com.jfireframework.context.event.ApplicationEvent;
import com.jfireframework.context.event.EventHandler;
import com.jfireframework.context.event.EventPublisher;

@Resource
public class HaftHandler implements EventHandler, ContextInitFinish
{
    @Resource
    private EventPublisher publisher;
    
    @Override
    public Enum<?> type()
    {
        return SmsEvent.halt;
    }
    
    @Override
    public void handle(ApplicationEvent event)
    {
        UserPhone myEvent = (UserPhone) event.getData();
        System.out.println("用户:" + myEvent.getPhone() + "欠费");
    }
    
    @Override
    public int getOrder()
    {
        // TODO Auto-generated method stub
        return 0;
    }
    
    @Override
    public void afterContextInit()
    {
        UserPhone phone = new UserPhone();
        phone.setPhone("1775032");
        publisher.publish(phone, SmsEvent.halt);
    }
    
}