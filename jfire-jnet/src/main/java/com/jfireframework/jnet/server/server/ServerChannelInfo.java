package com.jfireframework.jnet.server.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.TimeUnit;
import com.jfireframework.baseutil.collection.SingleProduceAndConsumerQueue;
import com.jfireframework.baseutil.collection.buffer.ByteBuf;
import com.jfireframework.baseutil.collection.buffer.DirectByteBuf;
import com.jfireframework.baseutil.disruptor.Disruptor;
import com.jfireframework.baseutil.reflect.ReflectUtil;
import com.jfireframework.baseutil.simplelog.ConsoleLogFactory;
import com.jfireframework.baseutil.simplelog.Logger;
import com.jfireframework.jnet.common.decodec.FrameDecodec;
import com.jfireframework.jnet.common.exception.BufNotEnoughException;
import com.jfireframework.jnet.common.exception.LessThanProtocolException;
import com.jfireframework.jnet.common.exception.NotFitProtocolException;
import com.jfireframework.jnet.common.handler.DataHandler;
import com.jfireframework.jnet.common.result.ServerInternalResult;
import com.jfireframework.jnet.server.CompletionHandler.ChannelReadHandler;
import com.jfireframework.jnet.server.CompletionHandler.ChannelWriteHandler;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class ServerChannelInfo
{
    public final static int                                           OPEN           = 1;
    public final static int                                           CLOSE          = 2;
    // 消息通道的打开状态
    private volatile int                                              openState      = OPEN;
    private static final long                                         openStateOff;
    private Disruptor                                                 disruptor;
    private FrameDecodec                                              frameDecodec;
    private ChannelReadHandler                                        channelReadHandler;
    private ChannelWriteHandler                                       channelWriteHandler;
    // 消息自身持有的socket通道
    private AsynchronousSocketChannel                                 channel;
    // 通道中进行数据读入的buffer
    private DirectByteBuf                                             ioBuf          = DirectByteBuf.allocate(120);
    private static Logger                                             logger         = ConsoleLogFactory.getLogger();
    // 读取超时时间
    private long                                                      readTimeout;
    // 最后一次读取时间
    private volatile long                                             lastReadTime;
    // 本次读取的截止时间
    private volatile long                                             endReadTime;
    // 启动读取超时的计数
    private volatile boolean                                          startCountdown = false;
    private long                                                      waitTimeout;
    private String                                                    address;
    private final SingleProduceAndConsumerQueue<ServerInternalResult> sendQueue      = new SingleProduceAndConsumerQueue<>();
    private DataHandler[]                                             handlers;
    private ServerInternalResult[]                                    results;
    private final int                                                 resultSize;
    private final static int                                          resultOffset;
    private final static int                                          resultShift;
    private final int                                                 resuleSizeMask;
    private static Unsafe                                             unsafe         = ReflectUtil.getUnsafe();
    private volatile long                                             cursor;
    private volatile long                                             wrapPoint;
    public final static int                                           CONTINUE_READ  = 1;
    // 暂时不监听监听当前通道上的数据
    public final static int                                           FREE_OF_READ   = 2;
    private volatile int                                              readState      = 0;
    public final static long                                          readStateOff;
    public final static int                                           IN_READ        = 1;
    public final static int                                           OUT_OF_READ    = 2;
                                                                                     
    static
    {
        readStateOff = ReflectUtil.getFieldOffset("readState", ServerChannelInfo.class);
        openStateOff = ReflectUtil.getFieldOffset("openState", ServerChannelInfo.class);
        resultOffset = unsafe.arrayBaseOffset(ServerInternalResult[].class);
        int scale = unsafe.arrayIndexScale(ServerInternalResult[].class);
        if (scale == 4)
        {
            resultShift = 2;
        }
        else if (scale == 8)
        {
            resultShift = 3;
        }
        else
        {
            throw new RuntimeException("错误的位数");
        }
    }
    
    public ServerChannelInfo(AsynchronousSocketChannel channel, int resultSize, Disruptor disruptor)
    {
        try
        {
            results = new ServerInternalResult[resultSize];
            address = channel.getRemoteAddress().toString();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        this.disruptor = disruptor;
        channelReadHandler = new ChannelReadHandler();
        channelWriteHandler = new ChannelWriteHandler();
        this.channel = channel;
        resuleSizeMask = resultSize - 1;
        this.resultSize = resultSize;
        wrapPoint = resultSize;
        
    }
    
    public int readState()
    {
        return readState;
    }
    
    public void setReadState(int state)
    {
        readState = state;
    }
    
    public boolean isAvailable(long cursor)
    {
        return cursor < this.cursor;
    }
    
    public void reStartRead()
    {
        if (readState == OUT_OF_READ)
        {
            if (unsafe.compareAndSwapInt(this, readStateOff, OUT_OF_READ, IN_READ))
            {
                doRead();
            }
        }
    }
    
    public void doRead()
    {
        while (true)
        {
            try
            {
                int result = frameAndHandle();
                if (result == CONTINUE_READ)
                {
                    startReadWait();
                    return;
                }
                else if (result == ServerChannelInfo.FREE_OF_READ)
                {
                    return;
                }
            }
            catch (NotFitProtocolException e)
            {
                logger.debug("协议错误，关闭链接");
                close(e);
                return;
            }
            catch (LessThanProtocolException e)
            {
                startReadWait();
                return;
            }
            catch (BufNotEnoughException e)
            {
                ioBuf.compact().ensureCapacity(e.getNeedSize());
                continueRead();
                return;
            }
            catch (Throwable e)
            {
                close(e);
                return;
            }
        }
    }
    
    public int frameAndHandle() throws Exception
    {
        while (true)
        {
            if (cursor >= wrapPoint)
            {
                for (int i = 0; i < 2; i++)
                {
                    wrapPoint = channelWriteHandler.cursor() + resultSize;
                    if (cursor < wrapPoint)
                    {
                        break;
                    }
                }
                if (cursor >= wrapPoint)
                {
                    readState = OUT_OF_READ;
                    return FREE_OF_READ;
                }
            }
            Object intermediateResult = frameDecodec.decodec(ioBuf);
            if (intermediateResult == null)
            {
                return CONTINUE_READ;
            }
            ServerInternalResult result = _getResult(cursor);
            if (result == null)
            {
                result = new ServerInternalResult(cursor, intermediateResult, this, 0);
                putResult(result, cursor);
            }
            else
            {
                result.init(cursor, intermediateResult, this, 0);
            }
            cursor += 1;
            for (int i = 0; i < handlers.length;)
            {
                intermediateResult = handlers[i].handle(intermediateResult, result);
                if (intermediateResult == DataHandler.skipToWorkRing)
                {
                    break;
                }
                if (i == result.getIndex())
                {
                    i++;
                    result.setIndex(i);
                }
                else
                {
                    i = result.getIndex();
                }
            }
            if (intermediateResult instanceof ByteBuf<?>)
            {
                result.setData(intermediateResult);
                result.flowDone();
                write(result);
            }
            if (ioBuf().remainRead() == 0)
            {
                return CONTINUE_READ;
            }
        }
        
    }
    
    private void putResult(ServerInternalResult result, long cursor)
    {
        unsafe.putObjectVolatile(results, resultOffset + ((cursor & resuleSizeMask) << resultShift), result);
    }
    
    public ServerInternalResult getResult(long cursor)
    {
        if (isAvailable(cursor))
        {
            return _getResult(cursor);
        }
        else
        {
            return null;
        }
    }
    
    private ServerInternalResult _getResult(long cursor)
    {
        Object result = unsafe.getObjectVolatile(results, resultOffset + ((cursor & resuleSizeMask) << resultShift));
        if (result == null)
        {
            return null;
        }
        else
        {
            return (ServerInternalResult) result;
        }
    }
    
    /**
     * 当前的socket通道是否打开
     * 
     * @return
     */
    public boolean isOpen()
    {
        return openState == OPEN;
    }
    
    /**
     * 关闭链接。 该方法会将自身状态设置为关闭，关闭本身所拥有的socket链接，从服务器注册状态中删除自身，将自身所持有的buffer返还给缓存池
     */
    public void close(Throwable exc)
    {
        if (openState == CLOSE)
        {
            return;
        }
        if (unsafe.compareAndSwapInt(this, openStateOff, OPEN, CLOSE))
        {
            try
            {
                ServerInternalResult result = new ServerInternalResult(-1, exc, this, 0);
                Object intermediateResult = exc;
                try
                {
                    for (DataHandler each : handlers)
                    {
                        intermediateResult = each.catchException(intermediateResult, result);
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                channel.close();
            }
            catch (IOException e)
            {
                logger.error("关闭通道异常", e);
            }
            ioBuf.release();
        }
    }
    
    public DirectByteBuf ioBuf()
    {
        return ioBuf;
    }
    
    public void setReadTimeout(long readTimeout)
    {
        this.readTimeout = readTimeout;
    }
    
    /**
     * 开始空闲读取等待，并且将倒数计时状态重置为false
     */
    public void startReadWait()
    {
        readState = IN_READ;
        startCountdown = false;
        channel.read(getWriteBuffer(), waitTimeout, TimeUnit.MILLISECONDS, this, channelReadHandler);
    }
    
    /**
     * 将iobuf的内容进行压缩，返回一个处于可写状态的ByteBuffer
     * 
     * @return
     */
    private ByteBuffer getWriteBuffer()
    {
        ioBuf.compact();
        ByteBuffer ioBuffer = ioBuf.nioBuffer();
        ioBuffer.position(ioBuffer.limit()).limit(ioBuffer.capacity());
        return ioBuffer;
    }
    
    /**
     * 在通道上继续读取未读取完整的数据
     */
    public void continueRead()
    {
        if (startCountdown == false)
        {
            lastReadTime = System.currentTimeMillis();
            endReadTime = lastReadTime + readTimeout;
            startCountdown = true;
        }
        channel.read(getWriteBuffer(), getRemainTime(), TimeUnit.MILLISECONDS, this, channelReadHandler);
        lastReadTime = System.currentTimeMillis();
    }
    
    /**
     * 剩余的读取消息时间
     * 
     * @return
     */
    private long getRemainTime()
    {
        return endReadTime - lastReadTime;
    }
    
    /**
     * 设置消息线路的等待时长
     * 
     * @param waitTimeout
     */
    public void setWaitTimeout(long waitTimeout)
    {
        this.waitTimeout = waitTimeout;
    }
    
    public AsynchronousSocketChannel getSocketChannel()
    {
        return channel;
    }
    
    public String getAddress()
    {
        return address;
    }
    
    /**
     * 将一个中间结果移交给业务线程进行处理
     * 
     * @param result
     */
    public void turnToWorkDisruptor(ServerInternalResult result)
    {
        disruptor.publish(result);
    }
    
    /**
     * 将一个ByteBuf写入到通道中。注意，该写入使用了AIO的异步模式。会在写入完成之后调用ChannelWriteHandler
     * 注意：用户最好不要调用这个方法。如果有需要写出的数据.可以设置InternalResult的index属性。结束当前的流程，框架会自动将数据写出
     * 
     * @param result
     */
    public void write(ServerInternalResult result)
    {
        if (result.tryWrite())
        {
            channel.write(((ByteBuf<?>) result.getData()).nioBuffer(), 10, TimeUnit.SECONDS, result, channelWriteHandler);
        }
    }
    
    public boolean isTopWriteResult(ServerInternalResult result)
    {
        return sendQueue.isTop(result);
    }
    
    public boolean canWrite(ServerInternalResult result)
    {
        return result.cursor() == channelWriteHandler.cursor();
    }
    
    public void sendOne()
    {
        sendQueue.poll();
    }
    
    public ServerInternalResult waitForSend()
    {
        return sendQueue.peek();
    }
    
    public void addWriteResult(ServerInternalResult packet)
    {
        sendQueue.add(packet);
    }
    
    public int left()
    {
        return sendQueue.size();
    }
    
    public void setHandlers(DataHandler... handlers)
    {
        this.handlers = handlers;
    }
    
    public DataHandler[] getHandlers()
    {
        return handlers;
    }
    
    public void setFrameDecodec(FrameDecodec frameDecodec)
    {
        this.frameDecodec = frameDecodec;
    }
    
}
