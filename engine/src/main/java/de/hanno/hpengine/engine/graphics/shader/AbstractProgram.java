package de.hanno.hpengine.engine.graphics.shader;

import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.event.GlobalDefineChangedEvent;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import net.engio.mbassy.listener.Handler;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;

import static org.lwjgl.opengl.GL42.GL_ATOMIC_COUNTER_BUFFER;


public abstract class AbstractProgram {

    protected HashMap<String, Uniform> uniforms = new HashMap<>();
    protected Defines defines = new Defines();
	protected final int id;

    public AbstractProgram(int id) { this.id = id; }
	
	public void use() {
		GL20.glUseProgram(getId());
	}
	
	protected void clearUniforms() { uniforms.clear(); }

	public void setUniform(String name, int value) {
		putInMapIfAbsent(name);
		uniforms.get(name).set(value);
	}
	public void setUniform(String name, boolean value) {
		int valueAsInt = value ? 1 : 0;
		putInMapIfAbsent(name);
		uniforms.get(name).set(valueAsInt);
	}
    public void setUniform(String name, float value) {
        putInMapIfAbsent(name);
        uniforms.get(name).set(value);
    }
    public void setUniform(String name, long value) {
        putInMapIfAbsent(name);
        uniforms.get(name).set(value);
    }
    LongBuffer longBuffer = null;
    public void setUniform(String name, long[] longs) {
        if(longBuffer == null) {
            longBuffer = BufferUtils.createLongBuffer(longs.length);
            longBuffer.rewind();
            longBuffer.put(longs);
        }
        setUniform(name, longBuffer);
    }

    public void setUniform(String name, LongBuffer buffer) {
        buffer.rewind();
        putInMapIfAbsent(name);
        uniforms.get(name).set(buffer);
    }
    public void setUniform(String name, double value) {
        putInMapIfAbsent(name);
        uniforms.get(name).set((float)value);
    }

	public void setUniformAsMatrix4(String name, FloatBuffer matrixBuffer) {
		putInMapIfAbsent(name);
		uniforms.get(name).setAsMatrix4(matrixBuffer);
	}
	public void setUniformAsMatrix4(String name, ByteBuffer matrixBuffer) {
		putInMapIfAbsent(name);
		uniforms.get(name).setAsMatrix4(matrixBuffer);
	}
	public void setUniform(String name, float x, float y, float z) {
		putInMapIfAbsent(name);
		uniforms.get(name).set(x, y, z);
	}
	public void setUniform(String name, Vector3f vec) {
		putInMapIfAbsent(name);
		uniforms.get(name).set(vec.x, vec.y, vec.z);
	}

	public void setUniformVector3ArrayAsFloatBuffer(String name, FloatBuffer values) {
		putInMapIfAbsent(name);
		uniforms.get(name).setVec3ArrayAsFloatBuffer(values);
	}

	public void setUniformFloatArrayAsFloatBuffer(String name, FloatBuffer values) {
		putInMapIfAbsent(name);
		uniforms.get(name).setFloatArrayAsFloatBuffer(values);
	}
	
	public void setUniformAsBlock(String name, float[] fs) {
		putBlockInMapIfAbsent(name);
		try {
			((UniformBlock) uniforms.get(name)).set(fs);
		} catch (ClassCastException e) {
			System.err.println("You can't set a non block uniform as block!");
			e.printStackTrace();
		}
	}

	private void putInMapIfAbsent(String name) {
		if (!uniforms.containsKey(name)) {
			uniforms.put(name, new Uniform(this, name));
		}
	}
	private void putBlockInMapIfAbsent(String name) {
		if (!uniforms.containsKey(name)) {
			uniforms.put(name, new UniformBlock(this, name));
		}
	}
	
	public int getUniformLocation(String name) {
		return GL20.glGetUniformLocation(getId(), name);
	}

	public void bindShaderStorageBuffer(int index, GPUBuffer block) {
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, block.getId());
	}
	public void bindShaderStorageBuffer(int index, PersistentMappedStructBuffer buffer) {
		GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, buffer.getId());
	}
	public void bindAtomicCounterBufferBuffer(int index, GPUBuffer block) {
		GL30.glBindBufferBase(GL_ATOMIC_COUNTER_BUFFER, index, block.getId());
	}

	public int getShaderStorageBlockIndex(String name) {
		return GL43.glGetProgramResourceIndex(getId(), GL43.GL_SHADER_STORAGE_BLOCK, name);
	}
	
	public void getShaderStorageBlockBinding(String name, int bindingIndex) {
		GL43.glShaderStorageBlockBinding(getId(), getShaderStorageBlockIndex(name), bindingIndex);
	}
	
	
	public Uniform getUniform(String key) {
		return uniforms.get(key);
	}
	public void addEmptyUniform(Uniform uniform) {
		uniforms.put(uniform.name, uniform);
	}

	public int getId() { return id; }

	@Subscribe
    @Handler
	public void handle(GlobalDefineChangedEvent e) {
	}

}
