package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.model.IndexBuffer;

import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

public class AtomicCounterBuffer extends IndexBuffer {
    public AtomicCounterBuffer(int size) {
        super(GL_SHADER_STORAGE_BUFFER);
        setCapacityInBytes(size * Integer.BYTES);
    }

    public void bindAsParameterBuffer() {
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, getId());
    }
}