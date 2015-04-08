package main.shader;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import main.World;
import main.model.DataChannels;
import main.renderer.Renderer;

import org.apache.commons.io.FileUtils;

public class ProgramFactory {

	public static String FIRSTPASS_DEFAULT_VERTEXSHADER_FILE = "first_pass_vertex.glsl";
	public static String FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE = "first_pass_fragment.glsl";
	
	private Renderer renderer;
	private World world;
	
	public static List<AbstractProgram> LOADED_PROGRAMS = new CopyOnWriteArrayList<>();

	public ProgramFactory(World world) {
		this.world = world;
		this.renderer = world.getRenderer();
	}

	public Program getProgram(String vertexShaderFilename, String fragmentShaderFileName) {
		Program program = new Program(renderer, null, vertexShaderFilename, fragmentShaderFileName, EnumSet.allOf(DataChannels.class), true, "");
		LOADED_PROGRAMS.add(program);
		World.getEventBus().register(program);
		return program;
	}
	
	public Program getProgram(String defines) {
		Program program = new Program(renderer, null, FIRSTPASS_DEFAULT_VERTEXSHADER_FILE, FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE, EnumSet.allOf(DataChannels.class), true, defines);
		LOADED_PROGRAMS.add(program);
		World.getEventBus().register(program);
		return program;
	}

	public ComputeShaderProgram getComputeProgram(String computeShaderLocation) {
		ComputeShaderProgram program = new ComputeShaderProgram(renderer, computeShaderLocation);
		LOADED_PROGRAMS.add(program);
		World.getEventBus().register(program);
		return program;
	}
	
	public Program getProgram(String vertexShaderFilename, String fragmentShaderFileName, EnumSet<DataChannels> channels, boolean needsTextures) {
		Program program = new Program(renderer, null, vertexShaderFilename, fragmentShaderFileName, channels, needsTextures, "");
		LOADED_PROGRAMS.add(program);
		World.getEventBus().register(program);
		return program;
	}

	public void copyDefaultFragmentShaderToFile(String name) throws IOException {
		name = name.endsWith(".glsl") ? name : name + ".glsl";
		FileUtils.copyFile(new File(Program.getDirectory() + FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE), new File(Program.getDirectory() + name));
	}
	
	public void copyDefaultVertexShaderToFile(String name) throws IOException {
		name = name.endsWith(".glsl") ? name : name + ".glsl";
		FileUtils.copyFile(new File(Program.getDirectory() + FIRSTPASS_DEFAULT_VERTEXSHADER_FILE), new File(Program.getDirectory() + name));
	}

}
