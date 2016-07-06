package com.jfireframework.licp.serializer.array;

import com.jfireframework.baseutil.collection.buffer.ByteBuf;
import com.jfireframework.licp.Licp;

public class IntArraySerializer extends AbstractArraySerializer
{
    
    public IntArraySerializer()
    {
        super(int[].class);
    }
    
    @Override
    public void serialize(Object src, ByteBuf<?> buf, Licp licp)
    {
        int[] array = (int[]) src;
        buf.writeInt(array.length);
        for (int each : array)
        {
            buf.writeInt(each);
        }
    }
    
    @Override
    public Object deserialize(ByteBuf<?> buf, Licp licp)
    {
        int length = buf.readInt();
        int[] array = new int[length];
        licp.putObject(array);
        for (int i = 0; i < length; i++)
        {
            array[i] = buf.readInt();
        }
        return array;
    }
    
}
