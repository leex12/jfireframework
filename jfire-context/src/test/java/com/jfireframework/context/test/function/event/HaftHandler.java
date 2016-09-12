package com.jfireframework.context.test.function.event;

import javax.annotation.Resource;
import com.jfireframework.context.ContextInitFinish;
import com.jfireframework.context.event.EventPoster;
import com.jfireframework.eventbus.event.ApplicationEvent;
import com.jfireframework.eventbus.event.Event;
import com.jfireframework.eventbus.handler.EventHandler;

@Resource
public class HaftHandler implements EventHandler<SmsEvent>, ContextInitFinish
{
    @Resource
    private EventPoster publisher;
    
    @Override
    public void handle(ApplicationEvent event)
    {
        System.out.println("asdasd");
        UserPhone myEvent = (UserPhone) event.getEventData();
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
        publisher.post(phone, SmsEvent.halt).await();
        ;
    }
    
    @Override
    public Enum<? extends Event<SmsEvent>> interest()
    {
        return SmsEvent.halt;
    }
    
}
