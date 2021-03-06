package com.jfireframework.jnet2.common.handler;

import com.jfireframework.jnet2.common.result.InternalResult;

public interface DataHandler
{
    public final Object skipToWorkRing = new Object();
    
    /**
     * 对传递过来的数据做处理。并且将处理完成的结果返回。后续的处理器会继续处理这个对象
     * 
     * @param data
     * @param entry
     * @throws Throwable 如果方法抛出了异常，则首先会执行捕获异常的动作。然后关闭该通道
     */
    public Object handle(Object data, InternalResult result) throws Throwable;
    
    /**
     * 通道发生异常是，处理链上的该方法会被调用
     * 
     * @param data
     * @param result
     * @return
     */
    public Object catchException(Object data, InternalResult result);
    
}
