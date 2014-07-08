package main.shader;

import java.util.EnumSet;

import main.model.DataChannels;
import main.renderer.Renderer;

public class ProgramFactory {

	public static String FIRSTPASS_DEFAULT_VERTEXSHADER_FILE = "first_pass_vertex.glsl";
	public static String FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE = "first_pass_fragment.glsl";
	
	private Renderer renderer;

	public ProgramFactory(Renderer renderer) {
		this.renderer = renderer;
	}

	public Program getProgram(String vertexShaderFilename, String fragmentShaderFileName) {
		return new Program(null, vertexShaderFilename, fragmentShaderFileName, EnumSet.allOf(DataChannels.class), true, "");
	}
	
	public Program getProgram(String defines) {
		return new Program(null, FIRSTPASS_DEFAULT_VERTEXSHADER_FILE, FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE, EnumSet.allOf(DataChannels.class), true, defines);
	}

	public Program getProgram(String vertexShaderFilename, String fragmentShaderFileName, EnumSet<DataChannels> channels, boolean needsTextures) {
		return new Program(null, vertexShaderFilename, fragmentShaderFileName, channels, needsTextures, "");
	}

}
