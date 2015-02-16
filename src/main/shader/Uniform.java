package main.shader;

import java.nio.FloatBuffer;

import main.World;

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
	public void set(double value) {
//		if(location == -1) { return; };
		GL20.glUniform1f(location, (float)value);
	}
	public void set(float x, float y, float z) {
//		if(location == -1) { return; };
		GL20.glUniform3f(location, x, y, z);
	}
	public void setAsMatrix4(FloatBuffer values) {
//		if(location == -1) { return; };
		GL20.glUniformMatrix4(location, false, values);
	}

	public void setVec3ArrayAsFloatBuffer(FloatBuffer values) {
		GL20.glUniform3(location, values);
	}
	public void setFloatArrayAsFloatBuffer(FloatBuffer values) {
		GL20.glUniform1(location, values);
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
