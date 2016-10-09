package engine.model;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL40;
import shader.AbstractPersistentMappedBuffer;
import shader.Bufferable;
import shader.OpenGLBuffer;
import shader.PersistentMappedBuffer;

import java.nio.*;

import static engine.model.CommandBuffer.DrawElementsIndirectCommand.sizeInBytes;
import static engine.model.CommandBuffer.DrawElementsIndirectCommand.getSizeInInts;
import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class CommandBuffer extends AbstractPersistentMappedBuffer<IntBuffer> {

    private static OpenGLBuffer globalCommandBuffer;
    public static synchronized OpenGLBuffer getGlobalCommandBuffer() {
        if (globalCommandBuffer == null) {
            globalCommandBuffer = new CommandBuffer(16000);
        }

        return globalCommandBuffer;
    }

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
        throw new IllegalStateException("Not implemented");
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

    public static class DrawElementsIndirectCommand implements Bufferable {
        public final int count;
        public final int primCount;
        public final int firstIndex;
        public final int baseVertex;
        public final int baseInstance;
        public final int entityOffset;

        public DrawElementsIndirectCommand(int count, int primCount, int firstIndex, int baseVertex, int baseInstance, int entityBaseIndex) {
            this.count = count;
            this.primCount = primCount;
            this.firstIndex = firstIndex;
            this.baseVertex = baseVertex;
            this.baseInstance = baseInstance;
            entityOffset = entityBaseIndex;
        }

        @Override
        public double[] get() {
            throw new IllegalStateException("Not implemented");
        }
        public int[] getAsInts() {
            return new int[] {count, primCount, firstIndex, baseVertex, baseInstance};
        }

        public static int sizeInBytes() {
            return getSizeInInts() * 4;
        }
        public static int getSizeInInts() {
            return 5;
        }
    }
}
