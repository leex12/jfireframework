package com.jfireframework.jnet2.server.CompletionHandler.weapon.single.read.async.push;

import com.jfireframework.baseutil.collection.buffer.ByteBuf;
import com.jfireframework.baseutil.concurrent.CpuCachePadingInt;
import com.jfireframework.eventbus.bus.EventBus;
import com.jfireframework.jnet2.common.channel.impl.ServerChannel;
import com.jfireframework.jnet2.server.CompletionHandler.weapon.single.read.async.AbstractAsyncSingleReadHandler;
import com.jfireframework.jnet2.server.CompletionHandler.weapon.single.write.push.SyncSingleWriteAndPushHandlerImpl;

public class AsyncSingleReadWithPushHandlerImpl extends AbstractAsyncSingleReadHandler
{
    private final static int        IDLE      = 0;
    private final static int        PENDING   = 1;
    private final static int        WORK      = 2;
    private final CpuCachePadingInt readState = new CpuCachePadingInt(0);
    
    public AsyncSingleReadWithPushHandlerImpl(ServerChannel serverChannel, EventBus eventBus)
    {
        super(serverChannel, eventBus);
        writeHandler = new SyncSingleWriteAndPushHandlerImpl(serverChannel, this);
    }
    
    @Override
    public void notifyRead()
    {
        int state = readState.value();
        if (state == WORK)
        {
            return;
        }
        if (state == PENDING)
        {
            do
            {
                state = readState.value();
            } while (state == PENDING);
        }
        if (readState.value() == IDLE && readState.compareAndSwap(IDLE, WORK))
        {
            if (ioBuf.remainRead() > 0)
            {
                doRead();
            }
            else
            {
                readAndWait();
            }
        }
    }
    
    @Override
    protected void doWrite(ByteBuf<?> buf)
    {
        readState.set(PENDING);
        writeHandler.write(buf);
        readState.set(IDLE);
    }
    
}
