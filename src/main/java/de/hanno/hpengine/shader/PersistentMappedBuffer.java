package de.hanno.hpengine.shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL43;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class PersistentMappedBuffer extends AbstractPersistentMappedBuffer<DoubleBuffer> {

    public PersistentMappedBuffer(int capacityInBytes) {
        this(capacityInBytes, GL43.GL_SHADER_STORAGE_BUFFER);
    }

    public PersistentMappedBuffer(int capacityInBytes, int target) {
        super(target);
        setCapacityInBytes(capacityInBytes);
    }

    @Override
    protected DoubleBuffer mapBuffer(int capacityInBytes, int flags) {
        DoubleBuffer newBuffer = glMapBufferRange(target, 0, capacityInBytes, flags, BufferUtils.createByteBuffer(capacityInBytes * getPrimitiveSizeInBytes())).asDoubleBuffer();
        if(buffer != null) {
            newBuffer.put(buffer);
            newBuffer.rewind();
        }
        return newBuffer;
    }

    @Override
    public FloatBuffer getValuesAsFloats() {
        FloatBuffer result = BufferUtils.createFloatBuffer(buffer.capacity());
        for(int i = 0; i < buffer.capacity(); i++) {
            result.put(i, (float) buffer.get(i));
        }

        result.rewind();
        return result;
    }

    @Override
    public int getPrimitiveSizeInBytes() {
        return 8;
    }

    @Override
    public DoubleBuffer getValues() {
        return getValues(0, buffer.capacity());
    }

    @Override
    public DoubleBuffer getValues(int offset, int length) {
        DoubleBuffer result = BufferUtils.createDoubleBuffer(length);
        for(int i = 0; i < length; i++) {
            result.put(i, buffer.get(offset+i));
        }

        result.rewind();
        return result;
    }

    @Override
    public void putValues(FloatBuffer values) {
        putValues(0, values);
    }

    @Override
    public void putValues(DoubleBuffer values) {
        putValues(0, values);
    }

    @Override
    public void putValues(int offset, FloatBuffer values) {
        if(values.capacity() > getSizeInBytes()) { setSizeInBytes(values.capacity());}
        bind();
        values.rewind();
        for(int i = offset; i < values.capacity(); i++) {
            buffer.put(i, values.get(i));
        }
        buffer.reset();
    }

    @Override
    public void putValues(int offset, DoubleBuffer values) {
        if(values == buffer) { return; }
        if(values.capacity() > getSizeInBytes()) { setSizeInBytes((offset + values.capacity() )* getPrimitiveSizeInBytes());}
        bind();
        values.rewind();
        buffer.position(offset);
        buffer.put(values);
    }

    @Override
    public void putValues(float... values) {
        putValues(0, values);
    }

    @Override
    public void putValues(int offset, float... values) {
        bind();
        buffer.position(offset);
        double[] doubleValues = new double[values.length];
        for(int i = 0; i < values.length; i++) {
            doubleValues[i] = values[i];
        }
        buffer.put(doubleValues);
    }

    @Override
    public void putValues(int offset, double... values) {
        bind();
        buffer.position(offset);
        buffer.put(values);
    }

    @Override
    public void put(Bufferable... bufferable) {
        put(0, bufferable);
    }

    @Override
    public void put(int offset, Bufferable... bufferable) {
        if(bufferable.length == 0) { return; }
        setCapacityInBytes(bufferable[0].getElementsPerObject() * getPrimitiveSizeInBytes() * bufferable.length);

        buffer.rewind();
        int currentOffset = 0;
        for (int i = 0; i < bufferable.length; i++) {
            Bufferable currentBufferable = bufferable[i];
            setCapacityInBytes((offset+currentOffset + currentBufferable.getElementsPerObject()) * getPrimitiveSizeInBytes());
            double[] currentBufferableArray = currentBufferable.get();
            for (int z = 0; z < currentBufferableArray.length; z++) {
                buffer.put(offset+currentOffset + z, currentBufferableArray[z]);
            }
            currentOffset += currentBufferable.getElementsPerObject();
        }
    }

}
