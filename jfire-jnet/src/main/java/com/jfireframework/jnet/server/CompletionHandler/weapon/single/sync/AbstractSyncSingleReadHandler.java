package com.jfireframework.jnet.server.CompletionHandler.weapon.single.sync;

import com.jfireframework.baseutil.collection.buffer.ByteBuf;
import com.jfireframework.jnet.common.channel.impl.ServerChannel;
import com.jfireframework.jnet.server.CompletionHandler.weapon.single.AbstractSingleReadHandler;

public abstract class AbstractSyncSingleReadHandler extends AbstractSingleReadHandler
{
    
    public AbstractSyncSingleReadHandler(ServerChannel serverChannel)
    {
        super(serverChannel);
    }
    
    protected void frameAndHandle() throws Throwable
    {
        while (true)
        {
            Object intermediateResult = frameDecodec.decodec(ioBuf);
            waeponTask.setChannelInfo(serverChannel);
            waeponTask.setData(intermediateResult);
            waeponTask.setIndex(0);
            for (int i = 0; i < handlers.length;)
            {
                intermediateResult = handlers[i].handle(intermediateResult, waeponTask);
                if (i == waeponTask.getIndex())
                {
                    i++;
                    waeponTask.setIndex(i);
                }
                else
                {
                    i = waeponTask.getIndex();
                }
            }
            if (intermediateResult instanceof ByteBuf<?>)
            {
                doWrite((ByteBuf<?>) intermediateResult);
                break;
            }
            else
            {
                if (ioBuf.remainRead() > 0)
                {
                    continue;
                }
                else
                {
                    readAndWait();
                    break;
                }
            }
        }
    }
    
    protected abstract void doWrite(ByteBuf<?> buf);
}