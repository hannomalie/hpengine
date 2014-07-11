package main.shader;

import java.nio.FloatBuffer;

import main.World;

import org.lwjgl.opengl.GL20;


public class Uniform {
	
	protected int location = -1;
	public String name = "";
	protected Program program;
	
	public Uniform(Program program, String name) {
		this.name = name;
		this.program = program;
		program.addEmptyUniform(this);
		setLocationIfAbsent();
	}

	public void set(int value) {
		GL20.glUniform1i(location, value);
	}
	public void set(float value) {
		GL20.glUniform1f(location, value);
	}
	public void set(double value) {
		GL20.glUniform1f(location, (float)value);
	}
	public void set(float x, float y, float z) {
		GL20.glUniform3f(location, x, y, z);
	}
	public void setAsMatrix4(FloatBuffer values) {
		GL20.glUniformMatrix4(location, false, values);
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
