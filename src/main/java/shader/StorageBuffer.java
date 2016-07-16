package shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import renderer.OpenGLContext;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

public abstract class StorageBuffer implements OpenGLBuffer {

    protected int id = -1;

    protected final int primitiveByteSize = 8; // 8 bytes for double primitve type

    protected ByteBuffer buffer;
    private int size = -1;

    private volatile DoubleBuffer tempBuffer;

    public StorageBuffer(int size) {
        this(BufferUtils.createDoubleBuffer(size));
    }

    public StorageBuffer(DoubleBuffer data) {
        OpenGLContext.getInstance().execute(() -> {
            id = GL15.glGenBuffers();
            buffer(data);
            unbind();
//            getValues();
        });
    }

    private void buffer(DoubleBuffer data) {
        bind();
        OpenGLContext.getInstance().execute(() -> {
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, data, GL15.GL_DYNAMIC_COPY);
            setSizeInBytes(GL15.glGetBufferParameter(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_BUFFER_SIZE));
        });
    }

    @Override
    public void bind() {
        OpenGLContext.getInstance().execute(() -> {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        });
    }

    @Override
    public void unbind() {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }



    @Override
    public FloatBuffer getValuesAsFloats() {
        final DoubleBuffer[] result = new DoubleBuffer[1];
        OpenGLContext.getInstance().execute(() -> {
            bind();
            buffer = GL15.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_READ_ONLY, null);
            result[0] = buffer.asDoubleBuffer(); // TODO: As read-only?
            GL15.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
            unbind();
        });
        double[] dst = new double[result[0].capacity()];
        result[0].get(dst);

        FloatBuffer resultFloatBuffer = BufferUtils.createFloatBuffer(dst.length);
        for(int i = 0; i < dst.length; i++) {
            float asFloat = (float) dst[i];
            resultFloatBuffer.put(asFloat);
        }
        result[0] = null;
        resultFloatBuffer.rewind();
        return resultFloatBuffer;
    }

    /**
     * @return The FloatBuffer with all the values of this buffer object
     */
    @Override
    public DoubleBuffer getValues() {
        final DoubleBuffer[] result = new DoubleBuffer[1];
        OpenGLContext.getInstance().execute(() -> {
            bind();
            buffer = GL15.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_READ_ONLY, null);
            result[0] = buffer.asDoubleBuffer(); // TODO: As read-only?
            GL15.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
            unbind();
        });
        return result[0];
    }

    /**
     * @param offset is the index of the first included float value of the buffer
     * @param length is the count of floats that are queried
     * @return The FloatBuffer that contains the queries values
     */
    @Override
    public DoubleBuffer getValues(int offset, int length) {
        bind();
        final DoubleBuffer[] result = new DoubleBuffer[1];
        OpenGLContext.getInstance().execute(() -> {
            result[0] = GL30.glMapBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, offset * primitiveByteSize, length * primitiveByteSize/*bytes!*/, GL30.GL_MAP_READ_BIT, null).asDoubleBuffer();
            GL15.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
            unbind();
        });
        return result[0];
    }

    @Override
    public Buffer getBuffer() {
        return null;
    }

    @Override
    public void putValues(FloatBuffer values) {
        putValues(0, values);
    }
    @Override
    public void putValues(DoubleBuffer values) {
        putValues(0, values);
    }

    /**
     * @param offset is the index of the first float value that will be overridden
     * @param values is the buffer with values that should be uploaded
     * @throws IndexOutOfBoundsException
     */
    @Override
    public void putValues(int offset, FloatBuffer values) {
        OpenGLContext.getInstance().execute(() -> {
            bind();
            if (offset * primitiveByteSize + values.capacity() * primitiveByteSize > size) {
                throw new IndexOutOfBoundsException(String.format("Can't put values into shader storage buffer %d (size: %d, offset %d, length %d)", id, size, offset * primitiveByteSize, values.capacity() * primitiveByteSize));
            }
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, offset * primitiveByteSize, values);
            unbind();
        });
    }
    @Override
    public void putValues(int offset, DoubleBuffer values) {
        OpenGLContext.getInstance().execute(() -> {
            bind();
            if (offset * primitiveByteSize + values.capacity() * primitiveByteSize > size) {
                throw new IndexOutOfBoundsException(String.format("Can't put values into shader storage buffer %d (size: %d, offset %d, length %d)", id, size, offset * primitiveByteSize, values.capacity() * primitiveByteSize));
            }
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, offset * primitiveByteSize, values);
            unbind();
        });
    }

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getSizeInBytes() {
        return size;
    }

    @Override
    public void setSizeInBytes(int size) {
        this.size = size;
    }

    @Override
    public void putValues(float... values) {
        putValues(0, values);
    }

    @Override
    public void putValues(int offset, float... values) {
        tempBuffer = BufferUtils.createDoubleBuffer(values.length);
        for (int i = 0; i < values.length; i++) {
            tempBuffer.put(offset + i, values[i]);
        }
        putValues(tempBuffer);
        tempBuffer = null;
    }

    @Override
    public void putValues(int offset, double... values) {
        tempBuffer = BufferUtils.createDoubleBuffer(values.length);
        for (int i = 0; i < values.length; i++) {
            tempBuffer.put(offset + i, values[i]);
        }
        putValues(tempBuffer);
        tempBuffer = null;
    }

    @Override
    public void put(int offset, Bufferable... bufferable) {
        if(bufferable.length == 0) { return; }
        OpenGLContext.getInstance().execute(() -> {
            tempBuffer = BufferUtils.createDoubleBuffer(bufferable[0].getElementsPerObject() * bufferable.length);
            for (int i = 0; i < bufferable.length; i++) {
                Bufferable currentBufferable = bufferable[i];
                int currentOffset = i * currentBufferable.getElementsPerObject();
                double[] currentBufferableArray = currentBufferable.get();
                for (int z = 0; z < currentBufferableArray.length; z++) {
                    tempBuffer.put(currentOffset + z, currentBufferableArray[z]);
                }
            }
            putValues(offset, tempBuffer);
        });
    }
}
