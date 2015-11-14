package shader;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import engine.AppContext;
import engine.model.DataChannels;
import renderer.OpenGLContext;
import renderer.Renderer;

import org.apache.commons.io.FileUtils;

public class ProgramFactory {

	public static String FIRSTPASS_DEFAULT_VERTEXSHADER_FILE = "first_pass_vertex.glsl";
	public static String FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE = "first_pass_fragment.glsl";
	
	private Renderer renderer;
	private AppContext appContext;
	
	public static List<AbstractProgram> LOADED_PROGRAMS = new CopyOnWriteArrayList<>();

	public ProgramFactory(AppContext appContext) {
		this.appContext = appContext;
		this.renderer = appContext.getRenderer();
	}

	public Program getProgram(String vertexShaderFilename, String fragmentShaderFileName) {
		Program program = new Program(vertexShaderFilename, null, fragmentShaderFileName, EnumSet.allOf(DataChannels.class), true, "");
		LOADED_PROGRAMS.add(program);
		AppContext.getEventBus().register(program);
		return program;
	}
	
	public Program getProgram(String defines) {
		Program program = new Program(FIRSTPASS_DEFAULT_VERTEXSHADER_FILE, null, FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE, EnumSet.allOf(DataChannels.class), true, defines);
		LOADED_PROGRAMS.add(program);
		AppContext.getEventBus().register(program);
		return program;
	}

	public ComputeShaderProgram getComputeProgram(String computeShaderLocation) {
        return OpenGLContext.getInstance().calculateWithOpenGLContext(() -> {
            ComputeShaderProgram program = new ComputeShaderProgram(renderer, computeShaderLocation);
            LOADED_PROGRAMS.add(program);
            AppContext.getEventBus().register(program);
            return program;
        });
	}

	public Program getProgram(String vertexShaderFilename, String fragmentShaderFileName, EnumSet<DataChannels> channels, boolean needsTextures) {
		return getProgram(vertexShaderFilename, null, fragmentShaderFileName, channels, needsTextures);
	}
	public Program getProgram(String vertexShaderFilename, String geometryShaderFileName, String fragmentShaderFileName, EnumSet<DataChannels> channels, boolean needsTextures) {
		return OpenGLContext.getInstance().calculateWithOpenGLContext(() -> {
            Program program = new Program(vertexShaderFilename, geometryShaderFileName, fragmentShaderFileName, channels, needsTextures, "");
            LOADED_PROGRAMS.add(program);
            AppContext.getEventBus().register(program);
            return program;
        });
	}

	public void copyDefaultFragmentShaderToFile(String name) throws IOException {
		name = name.endsWith(".glsl") ? name : name + ".glsl";
		FileUtils.copyFile(new File(Shader.getDirectory() + FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE), new File(Shader.getDirectory() + name));
	}
	
	public void copyDefaultVertexShaderToFile(String name) throws IOException {
		name = name.endsWith(".glsl") ? name : name + ".glsl";
		FileUtils.copyFile(new File(Shader.getDirectory() + FIRSTPASS_DEFAULT_VERTEXSHADER_FILE), new File(Shader.getDirectory() + name));
	}

    public VertexShader getDefaultFirstpassVertexShader() {
        return getDefaultFirstpassVertexShader();
    }
}
