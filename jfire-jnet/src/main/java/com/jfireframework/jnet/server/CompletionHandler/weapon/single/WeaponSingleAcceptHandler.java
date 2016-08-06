package com.jfireframework.jnet.server.CompletionHandler.weapon.single;

import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import com.jfireframework.baseutil.disruptor.Disruptor;
import com.jfireframework.baseutil.disruptor.EntryAction;
import com.jfireframework.baseutil.disruptor.waitstrategy.ParkWaitStrategy;
import com.jfireframework.baseutil.disruptor.waitstrategy.WaitStrategy;
import com.jfireframework.baseutil.simplelog.ConsoleLogFactory;
import com.jfireframework.baseutil.simplelog.Logger;
import com.jfireframework.baseutil.verify.Verify;
import com.jfireframework.jnet.common.channel.ChannelInitListener;
import com.jfireframework.jnet.common.channel.impl.ServerChannel;
import com.jfireframework.jnet.server.AioServer;
import com.jfireframework.jnet.server.CompletionHandler.AcceptHandler;
import com.jfireframework.jnet.server.CompletionHandler.WeaponReadHandler;
import com.jfireframework.jnet.server.CompletionHandler.weapon.single.async.SingleAsyncAction;
import com.jfireframework.jnet.server.CompletionHandler.weapon.single.async.push.AsyncSingleReadWithPushHandlerImpl;
import com.jfireframework.jnet.server.CompletionHandler.weapon.single.async.withoutpush.AsyncSingleReadHandlerImpl;
import com.jfireframework.jnet.server.CompletionHandler.weapon.single.sync.push.SyncSingleReadAndPushHandlerImpl;
import com.jfireframework.jnet.server.CompletionHandler.weapon.single.sync.withoutpush.SyncSingleReadHandlerImpl;
import com.jfireframework.jnet.server.util.PushMode;
import com.jfireframework.jnet.server.util.ServerConfig;
import com.jfireframework.jnet.server.util.WorkMode;

public class WeaponSingleAcceptHandler implements AcceptHandler
{
    private AioServer           aioServer;
    private Logger              logger = ConsoleLogFactory.getLogger();
    private ChannelInitListener initListener;
    private final PushMode      pushMode;
    private final WorkMode      workMode;
    private final Disruptor     disruptor;
    
    public WeaponSingleAcceptHandler(AioServer aioServer, ServerConfig serverConfig)
    {
        pushMode = serverConfig.getPushMode();
        workMode = serverConfig.getWorkMode();
        this.initListener = serverConfig.getInitListener();
        Verify.notNull(initListener, "initListener不能为空");
        this.aioServer = aioServer;
        initListener = serverConfig.getInitListener();
        if (workMode == WorkMode.ASYNC)
        {
            EntryAction[] actions = new EntryAction[serverConfig.getAsyncThreadSize()];
            for (int i = 0; i < actions.length; i++)
            {
                actions[i] = new SingleAsyncAction();
            }
            Thread[] threads = new Thread[actions.length];
            for (int i = 0; i < threads.length; i++)
            {
                threads[i] = new Thread(actions[i], "disruptor-" + i);
            }
            WaitStrategy waitStrategy = new ParkWaitStrategy(threads);
            disruptor = new Disruptor(serverConfig.getAsyncCapacity(), actions, threads, waitStrategy);
        }
        else
        {
            disruptor = null;
        }
    }
    
    @Override
    public void completed(AsynchronousSocketChannel socketChannel, Object attachment)
    {
        try
        {
            ServerChannel channelInfo = new ServerChannel();
            channelInfo.setChannel(socketChannel);
            initListener.channelInit(channelInfo);
            WeaponReadHandler readHandler = null;
            if (workMode == WorkMode.SYNC)
            {
                if (pushMode == PushMode.OFF)
                {
                    readHandler = new SyncSingleReadHandlerImpl(channelInfo);
                }
                else
                {
                    readHandler = new SyncSingleReadAndPushHandlerImpl(channelInfo);
                }
            }
            else
            {
                
                if (pushMode == PushMode.OFF)
                {
                    readHandler = new AsyncSingleReadHandlerImpl(channelInfo, disruptor);
                }
                else
                {
                    readHandler = new AsyncSingleReadWithPushHandlerImpl(channelInfo, disruptor);
                }
                
            }
            readHandler.readAndWait();
            aioServer.getServerSocketChannel().accept(null, this);
        }
        catch (Exception e)
        {
            logger.error("注册异常", e);
        }
    }
    
    @Override
    public void failed(Throwable exc, Object attachment)
    {
        if (exc instanceof AsynchronousCloseException)
        {
            logger.info("服务端监听链接被关闭");
        }
        else if (exc instanceof ClosedChannelException)
        {
            logger.info("服务端监听链接被关闭");
        }
        else
        {
            logger.error("链接异常关闭", exc);
        }
        Thread.currentThread().interrupt();
    }
    
    @Override
    public void stop()
    {
        if (disruptor != null)
        {
            disruptor.stop();
        }
    }
}
