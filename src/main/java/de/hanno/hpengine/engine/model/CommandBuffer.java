package de.hanno.hpengine.engine.model;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL40;
import de.hanno.hpengine.shader.AbstractPersistentMappedBuffer;
import de.hanno.hpengine.shader.Bufferable;

import java.nio.*;

import static de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand.sizeInBytes;
import static de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand.getSizeInInts;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class CommandBuffer extends AbstractPersistentMappedBuffer<IntBuffer> {

    public CommandBuffer(int capacityInBytes) {
        super(GL40.GL_DRAW_INDIRECT_BUFFER);
        setCapacityInBytes(capacityInBytes);
    }

    @Override
    protected IntBuffer mapBuffer(int capacityInBytes, int flags) {
        IntBuffer newBuffer = glMapBufferRange(target, 0, capacityInBytes, flags, BufferUtils.createByteBuffer(capacityInBytes * getPrimitiveSizeInBytes())).asIntBuffer();

        if(buffer != null) {
            newBuffer.put(buffer);
            newBuffer.rewind();
        }
        return newBuffer;
    }

    @Override
    public int getPrimitiveSizeInBytes() {
        return sizeInBytes();
    }

    @Override
    public FloatBuffer getValuesAsFloats() {
        FloatBuffer result = BufferUtils.createFloatBuffer(buffer.capacity() / getPrimitiveSizeInBytes());
        for(int i = 0; i < buffer.capacity() / getPrimitiveSizeInBytes(); i++) {
            result.put(i, (float) buffer.get(i));
        }
        result.rewind();
        return result;
    }

    @Override
    public Buffer getValues() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public Buffer getValues(int offset, int length) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(FloatBuffer values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(DoubleBuffer values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(int offset, FloatBuffer values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(int offset, DoubleBuffer values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(float... values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(int offset, float... values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(int offset, double... values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void put(int offset, Bufferable[] bufferable) {
        if(bufferable.length == 0) { return; }

        if(!bufferable[0].getClass().equals(DrawElementsIndirectCommand.class)) { throw new IllegalArgumentException("Use only commands!"); }

        setCapacityInBytes((offset + bufferable.length) * sizeInBytes());

        buffer.rewind();
        for (int i = 0; i < bufferable.length; i++) {
            Bufferable currentBufferable = bufferable[i];
            DrawElementsIndirectCommand command = (DrawElementsIndirectCommand) currentBufferable;
            int currentOffset = i * getSizeInInts();
            int[] currentBufferablesValues = command.getAsInts();
            for (int z = 0; z < currentBufferablesValues.length; z++) {
                buffer.put(offset+currentOffset + z, currentBufferablesValues[z]);
            }
        }
    }

    @Override
    public void put(Bufferable[] bufferable) {
        put(0, bufferable);
    }

    public final static class DrawElementsIndirectCommand implements Bufferable {
        public int count;
        public int primCount;
        public int firstIndex;
        public int baseVertex;
        public int baseInstance;
        public int entityOffset;
        private int[] asInts = new int[6];

        public DrawElementsIndirectCommand() {

        }

        public DrawElementsIndirectCommand(int count, int primCount, int firstIndex, int baseVertex, int baseInstance, int entityBaseIndex) {
            init(count, primCount, firstIndex, baseVertex, baseInstance, entityBaseIndex);
        }

        public void init(int count, int primCount, int firstIndex, int baseVertex, int baseInstance, int entityBaseIndex) {
            this.count = count;
            this.primCount = primCount;
            this.firstIndex = firstIndex;
            this.baseVertex = baseVertex;
            this.baseInstance = baseInstance;
            entityOffset = entityBaseIndex;
            asInts[0] = count;
            asInts[1] = primCount;
            asInts[2] = firstIndex;
            asInts[3] = baseVertex;
            asInts[4] = baseInstance;
        }

        @Override
        public double[] get() {
            throw new IllegalStateException("Not implemented");
        }
        public int[] getAsInts() {
            return asInts;
        }

        public static int sizeInBytes() {
            return getSizeInInts() * 4;
        }
        public static int getSizeInInts() {
            return 5;
        }
    }
}
