package com.jfireframework.jnet.server.CompletionHandler;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.TimeUnit;
import com.jfireframework.baseutil.collection.buffer.ByteBuf;
import com.jfireframework.jnet.common.channel.ServerChannelInfo;
import com.jfireframework.jnet.common.result.ServerInternalResult;

public class BatchWriteCompletionHandler implements CompletionHandler<Long, ByteBuf[]>
{
	private volatile long			cursor		= 0;
	private ReadCompletionHandler	readCompletionHandler;
	private long					wrapPoint	= 0;
	private static final int		retryPermit	= 2;
	private final ServerChannelInfo	channelInfo;
	
	public BatchWriteCompletionHandler(ReadCompletionHandler readCompletionHandler, ServerChannelInfo channelInfo)
	{
		this.readCompletionHandler = readCompletionHandler;
		this.channelInfo = channelInfo;
	}
	
	public long cursor()
	{
		return cursor;
	}
	
	@Override
	public void completed(Long writeTotal, ByteBuf[] bufs)
	{
		try
		{
			boolean writeAll = true;
			for (ByteBuf<?> each : bufs)
			{
				int remain = each.remainRead();
				if (remain > 0)
				{
					if (writeTotal >= remain)
					{
						writeTotal -= remain;
						each.readIndex(each.writeIndex());
					}
					else
					{
						each.addReadIndex(writeTotal.intValue());
						writeAll = false;
						break;
					}
				}
			}
			if (writeAll == false)
			{
				ByteBuffer[] buffers = new ByteBuffer[bufs.length];
				for (int i = 0; i < bufs.length; i++)
				{
					buffers[i] = bufs[i].nioBuffer();
				}
				channelInfo.getChannel().write(buffers, 0, bufs.length, 10, TimeUnit.SECONDS, bufs, this);
				return;
			}
			else
			{
				long version = cursor + 1;
				int tryCount = 0;
				while (true)
				{
					/**
					 * 这里必须进行可用性判断。如果不检测的话，会拿取到未正确初始化的数据导致进行了错误处理 具体错误是这样的：
					 * 拿到一个本来不可写的数据，但是读取线程已经准备重新初始化这个result了，但是只是初始化到flowState=
					 * undone前一步，这样可写判断就可以通过。
					 * 通过之后在获取bytebuffer写出前，读取线程就可能在处理这个数据，导致数据的紊乱或者别的问题
					 */
					if (version < wrapPoint)
					{
						// 可用的情况下，必然能够拿到数据。所以不需要为空判断
						ServerInternalResult next = (ServerInternalResult) channelInfo.getResult(version);
						// 由于写操作的序号没有前进，这个方法中的写无需cas，可以直接赋值
						if (next.tryWrite(version))
						{
							boolean batchwrite = false;
							do
							{
								long _version = version + 1;
								next = (ServerInternalResult) channelInfo.getResult(_version);
								if (_version < wrapPoint)
								{
									if (next.tryWrite(_version) == false)
									{
										version = _version;
										break;
									}
									batchwrite = true;
									version = _version;
								}
								else
								{
									break;
								}
							} while (true);
							if (batchwrite)
							{
								int length = (int) (version - cursor + 1);
								ByteBuffer[] buffers = new ByteBuffer[length];
								ByteBuf<?>[] newBufs = new ByteBuf<?>[length];
								for (int i = 0; i < length; i++)
								{
									newBufs[i] = (ByteBuf<?>) ((ServerInternalResult) channelInfo.getResult(i + cursor + 1)).getData();
									buffers[i] = newBufs[i].nioBuffer();
								}
								cursor = version - 1;
								readCompletionHandler.reStartRead();
								channelInfo.getChannel().write(buffers, 0, length, 10, TimeUnit.SECONDS, newBufs, this);
							}
							else
							{
								cursor = version;
								// 重启读取必须在更新了cursor之后，否则因为没有下一个可以容纳的空间，一重启读取又进入了等待读取状态。
								// 由于上一步更新了cursor，所以下面的操作都存在并发的可能性，那么重启读取要保证只能被一个线程真正激发一次。否则就会造成多重读取异常。这依靠方法内的cas完成。
								readCompletionHandler.reStartRead();
								// next已经在上面的tryWrite被抢占了写入权限，这里可以直接写出而不需要担心
								next.directWrite();
							}
						}
					}
					tryCount += 1;
					if (tryCount < retryPermit)
					{
						wrapPoint = readCompletionHandler.cursor();
					}
					else
					{
						break;
					}
				}
				cursor = version;
				readCompletionHandler.reStartRead();
				wrapPoint = readCompletionHandler.cursor();
				if (version < wrapPoint)
				{
					ServerInternalResult next = (ServerInternalResult) channelInfo.getResult(version);
					next.write(version);
				}
				// 这一步最无关紧要，也不太可能引起异常，放到最后一步执行，这样也避免下面异常捕获的时候出现释放两次的情况
				for (ByteBuf<?> each : bufs)
				{
					each.release();
				}
			}
		}
		catch (Exception e)
		{
			for (ByteBuf<?> each : bufs)
			{
				each.release();
			}
			readCompletionHandler.catchThrowable(e);
		}
	}
	
	@Override
	public void failed(Throwable exc, ByteBuf[] bufs)
	{
		for (ByteBuf<?> each : bufs)
		{
			each.release();
		}
		readCompletionHandler.catchThrowable(exc);
	}
	
}