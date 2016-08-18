package com.jfireframework.jnet2.server.CompletionHandler.weapon.single.write.push;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import com.jfireframework.baseutil.collection.buffer.ByteBuf;
import com.jfireframework.baseutil.concurrent.CpuCachePadingInt;
import com.jfireframework.baseutil.concurrent.MPSCLinkedQueue;
import com.jfireframework.baseutil.simplelog.ConsoleLogFactory;
import com.jfireframework.baseutil.simplelog.Logger;
import com.jfireframework.jnet2.common.channel.impl.ServerChannel;
import com.jfireframework.jnet2.server.CompletionHandler.WeaponReadHandler;
import com.jfireframework.jnet2.server.CompletionHandler.WeaponWriteHandler;

public class SyncSingleWriteAndPushHandlerImpl implements WeaponWriteHandler
{
    private final ServerChannel         serverChannel;
    private final WeaponReadHandler     readHandler;
    private Logger                      logger       = ConsoleLogFactory.getLogger();
    private static final int            IDLE         = 0;
    private static final int            WORK         = 1;
    private CpuCachePadingInt           writeState    = new CpuCachePadingInt(IDLE);
    private MPSCLinkedQueue<ByteBuf<?>> pushQueue = new MPSCLinkedQueue<>();
    
    public SyncSingleWriteAndPushHandlerImpl(ServerChannel serverChannel, WeaponReadHandler readHandler)
    {
        this.serverChannel = serverChannel;
        this.readHandler = readHandler;
    }
    
    @Override
    public void completed(Integer result, ByteBuf<?> buf)
    {
        ByteBuffer buffer = buf.cachedNioBuffer();
        if (buffer.hasRemaining())
        {
            serverChannel.getSocketChannel().write(buffer, 10, TimeUnit.SECONDS, buf, this);
            return;
        }
        buf.release();
        buf = pushQueue.poll();
        if (buf != null)
        {
            serverChannel.getSocketChannel().write(buf.cachedNioBuffer(), 10, TimeUnit.SECONDS, buf, this);
            return;
        }
        writeState.set(IDLE);
        while (true)
        {
            buf = pushQueue.poll();
            if (buf == null)
            {
                readHandler.notifyRead();
                break;
            }
            else
            {
                if (writeState.compareAndSwap(IDLE, WORK))
                {
                    buf = pushQueue.poll();
                    if (buf != null)
                    {
                        serverChannel.getSocketChannel().write(buf.cachedNioBuffer(), 10, TimeUnit.SECONDS, buf, this);
                        break;
                    }
                    else
                    {
                        writeState.set(IDLE);
                        continue;
                    }
                }
                else
                {
                    break;
                }
            }
        }
    }
    
    @Override
    public void failed(Throwable exc, ByteBuf<?> buf)
    {
        logger.error("error", exc);
        buf.release();
        readHandler.catchThrowable(exc);
    }
    
    @Override
    public void write(ByteBuf<?> buf)
    {
        push(buf);
    }
    
    @Override
    public void push(ByteBuf<?> buf)
    {
        if (writeState.value() == IDLE && writeState.compareAndSwap(IDLE, WORK))
        {
            serverChannel.getSocketChannel().write(buf.cachedNioBuffer(), 10, TimeUnit.SECONDS, buf, this);
        }
        else
        {
            pushQueue.add(buf);
            while (writeState.value() == IDLE && writeState.compareAndSwap(IDLE, WORK))
            {
                buf = pushQueue.poll();
                if (buf != null)
                {
                    serverChannel.getSocketChannel().write(buf.cachedNioBuffer(), 10, TimeUnit.SECONDS, buf, this);
                    break;
                }
                else
                {
                    writeState.set(IDLE);
                    if (pushQueue.isEmpty())
                    {
                        break;
                    }
                    else
                    {
                        continue;
                    }
                }
            }
        }
    }
    
}
