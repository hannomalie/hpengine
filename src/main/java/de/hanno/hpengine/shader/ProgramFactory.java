package de.hanno.hpengine.shader;

import de.hanno.hpengine.engine.Engine;
import org.apache.commons.io.FileUtils;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.shader.define.Define;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.hanno.hpengine.shader.Shader.*;

public class ProgramFactory {

	public static ShaderSource FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE;
    public static ShaderSource FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE;
    public static Program FIRSTPASS_DEFAULT_PROGRAM;

    static {
        try {
            FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE = ShaderSourceFactory.getShaderSource(new File(getDirectory() + "first_pass_vertex.glsl"));
            FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE = ShaderSourceFactory.getShaderSource(new File(getDirectory() + "first_pass_fragment.glsl"));
        } catch (Exception e) {
            System.err.println("Not able to load default vertex and fragment de.hanno.hpengine.shader sources...");
            System.exit(-1);
        }
    }

	public static List<AbstractProgram> LOADED_PROGRAMS = new CopyOnWriteArrayList<>();

    private static ProgramFactory instance = null;
    public static ProgramFactory getInstance() {
        if(instance == null) {
            throw new IllegalStateException("Call ProgramFactory.init() before using it");
        }
        return instance;
    }

	private ProgramFactory() {
	}

    public static void init() {
        instance = new ProgramFactory();
    }

    public Program getProgram(String vertexShaderFilename, String fragmentShaderFileName) throws Exception {
        ShaderSource vertexShaderSource = ShaderSourceFactory.getShaderSource(new File(getDirectory() + vertexShaderFilename));
        ShaderSource fragmentShaderSource = ShaderSourceFactory.getShaderSource(new File(getDirectory() + fragmentShaderFileName));

        return getProgram(vertexShaderSource, fragmentShaderSource);
    }
    public Program getProgram(ShaderSource vertexShaderSource, ShaderSource fragmentShaderSource) throws IOException {

        Program program = new Program(vertexShaderSource, null, fragmentShaderSource, true, "");

		LOADED_PROGRAMS.add(program);
		Engine.getEventBus().register(program);
		return program;
	}
	
	public Program getProgram(String defines) {
		Program program = new Program(FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE, null, FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE, true, defines);
		LOADED_PROGRAMS.add(program);
		Engine.getEventBus().register(program);
		return program;
	}

    public ComputeShaderProgram getComputeProgram(String computeShaderLocation) {
        return getComputeProgram(computeShaderLocation, Collections.EMPTY_LIST);
    }
	public ComputeShaderProgram getComputeProgram(String computeShaderLocation, List<Define> defines) {
        return OpenGLContext.getInstance().calculate(() -> {
            ComputeShaderProgram program = new ComputeShaderProgram(ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + computeShaderLocation)), defines);
            LOADED_PROGRAMS.add(program);
            Engine.getEventBus().register(program);
            return program;
        });
	}

	public Program getProgram(String vertexShaderFilename, String fragmentShaderFileName, boolean needsTextures) {
		return getProgram(vertexShaderFilename, null, fragmentShaderFileName, needsTextures);
	}
	public Program getProgram(String vertexShaderFilename, String geometryShaderFileName, String fragmentShaderFileName, boolean needsTextures) {
		return OpenGLContext.getInstance().calculate(() -> {
            ShaderSource vertexShaderSource = ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + vertexShaderFilename));
            ShaderSource fragmentShaderSource = ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + fragmentShaderFileName));
            ShaderSource geometryShaderSource = ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + geometryShaderFileName));

            Program program = new Program(vertexShaderSource, geometryShaderSource, fragmentShaderSource, needsTextures, "");
            LOADED_PROGRAMS.add(program);
            Engine.getEventBus().register(program);
            return program;
        });
	}

    public Program getFirstpassDefaultProgram() {
        if(FIRSTPASS_DEFAULT_PROGRAM == null) {
            try {
                FIRSTPASS_DEFAULT_PROGRAM = getProgram(FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE, FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return FIRSTPASS_DEFAULT_PROGRAM;
    }

	public void copyDefaultFragmentShaderToFile(String name) throws IOException {
		name = name.endsWith(".glsl") ? name : name + ".glsl";
		FileUtils.copyFile(new File(getDirectory() + FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE), new File(getDirectory() + name));
	}
	
	public void copyDefaultVertexShaderToFile(String name) throws IOException {
		name = name.endsWith(".glsl") ? name : name + ".glsl";
		FileUtils.copyFile(new File(getDirectory() + FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE), new File(getDirectory() + name));
	}

    public VertexShader getDefaultFirstpassVertexShader() {
        try {
            return VertexShader.load(FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Default vertex de.hanno.hpengine.shader cannot be loaded...");
            System.exit(-1);
        }
        return null;
    }
}