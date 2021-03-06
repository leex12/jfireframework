package com.jfireframework.jnet2.common.channel.impl;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;
import com.jfireframework.jnet2.client.ResponseFuture;
import com.jfireframework.jnet2.common.channel.ClientChannel;

public class FutureClientChannelInfo extends AbstractChannel implements ClientChannel
{
    
    class feaureHolder
    {
        private ResponseFuture future;
    }
    
    private feaureHolder[]   holders;
    private Thread           writeThread;
    private volatile boolean needUnpark   = false;
    private final int        mask;
    private final int        capacity;
    private long             wrap         = 0;
    private volatile long    putCursor    = 0;
    private volatile long    singalCursor = 0;
    
    public FutureClientChannelInfo(int capacity, AsynchronousSocketChannel socketChannel)
    {
        super(socketChannel);
        int tmp = 1;
        while (tmp < capacity)
        {
            tmp <<= 1;
        }
        capacity = tmp;
        holders = new feaureHolder[capacity];
        for (int i = 0; i < capacity; i++)
        {
            holders[i] = new feaureHolder();
        }
        this.capacity = capacity;
        mask = capacity - 1;
    }
    
    public final void signal(Object obj)
    {
        // 可以考虑一种很难出现的情况就是服务端收到一个请求的时候发送了两个响应。这样就会导致读取到不合适位置的future
        ResponseFuture future = holders[(int) (singalCursor & mask)].future;
        future.ready(obj, null);
        singalCursor += 1;
        if (needUnpark)
        {
            // 这里设置为false，避免不必要的对一个线程进行unpark操作
            needUnpark = false;
            LockSupport.unpark(writeThread);
        }
    }
    
    public final void signalAll(Throwable e)
    {
        ResponseFuture future;
        long current = singalCursor;
        while (current < putCursor)
        {
            future = holders[(int) (current & mask)].future;
            future.ready(null, e);
            current += 1;
        }
        singalCursor = current;
        if (needUnpark)
        {
            needUnpark = false;
            LockSupport.unpark(writeThread);
        }
    }
    
    public Future<?> addFuture()
    {
        long current = putCursor;
        while (current >= wrap)
        {
            wrap = singalCursor + capacity;
            if (current < wrap)
            {
                break;
            }
            // writeThread的赋值要在needUnpark的前面，这样才能保证其他线程中该属性的正确可见
            writeThread = Thread.currentThread();
            needUnpark = true;
            // 在设置needUnpark之后进行检查，以避免数据其实都已经被读取而没有线程可以unpark该线程
            wrap = singalCursor + capacity;
            if (current >= wrap)
            {
                LockSupport.park();
            }
            else
            {
                // needUnpark = false;
                break;
            }
        }
        // 客户端每一个future都代表一个结果，不可以被复用。因为外部环境需要保留所有的result
        ResponseFuture future = new ResponseFuture();
        holders[(int) (current & mask)].future = future;
        putCursor = current + 1;
        return future;
    }
}
