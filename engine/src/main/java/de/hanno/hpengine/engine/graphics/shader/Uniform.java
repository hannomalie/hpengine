package de.hanno.hpengine.engine.graphics.shader;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;

import org.lwjgl.opengl.ARBBindlessTexture;
import org.lwjgl.opengl.GL20;


public class Uniform {
	
	protected int location = -1;
	public String name = "";
	protected AbstractProgram program;
	
	public Uniform(AbstractProgram iProgram, String name) {
		this.name = name;
		this.program = iProgram;
		iProgram.addEmptyUniform(this);
		setLocationIfAbsent();
	}

	public void set(int value) {
//		if(location == -1) { return; };
		GL20.glUniform1i(location, value);
	}
	public void set(float value) {
//		if(location == -1) { return; };
		GL20.glUniform1f(location, value);
	}
    public void set(long value) {
//		if(location == -1) { return; };
        ARBBindlessTexture.glUniformHandleui64ARB(location, value);
    }
	public void set(float x, float y, float z) {
//		if(location == -1) { return; };
		GL20.glUniform3f(location, x, y, z);
	}
	public void setAsMatrix4(FloatBuffer values) {
//		if(location == -1) { return; };
		GL20.glUniformMatrix4fv(location, false, values);
	}
	public void setAsMatrix4(ByteBuffer values) {
//		if(location == -1) { return; };
		GL20.glUniformMatrix4fv(location, false, values.asFloatBuffer());
	}

    public void set(LongBuffer values) {
        ARBBindlessTexture.glUniformHandleui64vARB(location, values);
    }

	public void setVec3ArrayAsFloatBuffer(FloatBuffer values) {
		GL20.glUniform3fv(location, values);
	}
	public void setFloatArrayAsFloatBuffer(FloatBuffer values) {
		GL20.glUniform1fv(location, values);
	}
	
	public Uniform setLocationIfAbsent() {
		if (location == -1) {
			location = program.getUniformLocation(name);
		}
		return this;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Uniform)) {
			return false;
		}
		if (((Uniform) obj).name == name && ((Uniform) obj).program == program) {
			return true;
		}
		return false;
	}
	
	@Override
	public String toString() {
		return name + "@" + location;
	}

}
