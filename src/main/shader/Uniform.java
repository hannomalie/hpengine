package main.shader;

import java.nio.FloatBuffer;

import main.World;

import org.lwjgl.opengl.GL20;


public class Uniform {
	
	private int location = -1;
	public String name = "";
	private Program program;
	
	public Uniform(Program program, String name) {
		this.name = name;
		this.program = program;
		program.addEmptyUniform(this);
	}

	public void set(int value) {
		setLocationIfAbsent();
		GL20.glUniform1i(location, value);
	}
	public void set(float value) {
		setLocationIfAbsent();
		GL20.glUniform1f(location, value);
	}
	public void set(double value) {
		setLocationIfAbsent();
		GL20.glUniform1f(location, (float)value);
	}
	public void set(float x, float y, float z) {
		setLocationIfAbsent();
		GL20.glUniform3f(location, x, y, z);
	}
	public void setAsMatrix4(FloatBuffer values) {
		setLocationIfAbsent();
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
