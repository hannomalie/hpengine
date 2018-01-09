package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.util.ressources.CodeSource;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.hanno.hpengine.engine.graphics.shader.Shader.*;

public class ProgramFactory {

    public CodeSource FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE;
    public CodeSource FIRSTPASS_ANIMATED_DEFAULT_VERTEXSHADER_SOURCE;
    public CodeSource FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE;

    public Program FIRSTPASS_DEFAULT_PROGRAM;
    public Program FIRSTPASS_ANIMATED_DEFAULT_PROGRAM;

    public ComputeShaderProgram HIGHZ_PROGRAM;
    public Program APPEND_DRAWCOMMANDS_PROGRAM;

	public static List<AbstractProgram> LOADED_PROGRAMS = new CopyOnWriteArrayList<>();

    private static ProgramFactory instance = null;


    public static ProgramFactory getInstance() {
        if(instance == null) {
            throw new IllegalStateException("Call ProgramFactory.init() before using it");
        }
        return instance;
    }

	private ProgramFactory() {
        try {
            FIRSTPASS_ANIMATED_DEFAULT_VERTEXSHADER_SOURCE = ShaderSourceFactory.getShaderSource(new File(getDirectory() + "first_pass_animated_vertex.glsl"));
            FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE = ShaderSourceFactory.getShaderSource(new File(getDirectory() + "first_pass_vertex.glsl"));
            FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE = ShaderSourceFactory.getShaderSource(new File(getDirectory() + "first_pass_fragment.glsl"));

            FIRSTPASS_DEFAULT_PROGRAM = getProgram(FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE, FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE, new Defines());
            FIRSTPASS_ANIMATED_DEFAULT_PROGRAM = getProgram(FIRSTPASS_ANIMATED_DEFAULT_VERTEXSHADER_SOURCE, FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE, new Defines());

            APPEND_DRAWCOMMANDS_PROGRAM = getProgramFromFileNames("append_drawcommands_vertex.glsl", null, new Defines());
            HIGHZ_PROGRAM = getComputeProgram("highZ_compute.glsl");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Not able to load default vertex and fragment shader sources...");
            System.exit(-1);
        }
    }

    public static void init() {
        instance = new ProgramFactory();
    }

    public Program getProgramFromFileNames(String vertexShaderFilename, String fragmentShaderFileName, Defines defines) throws Exception {
        CodeSource vertexShaderSource = ShaderSourceFactory.getShaderSource(new File(getDirectory() + vertexShaderFilename));
        CodeSource fragmentShaderSource = ShaderSourceFactory.getShaderSource(new File(getDirectory() + fragmentShaderFileName));

        return getProgram(vertexShaderSource, fragmentShaderSource, defines);
    }
	public Program getProgram(Defines defines) {
		Program program = new Program(FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE, null, FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE, defines);
		LOADED_PROGRAMS.add(program);
		Engine.getEventBus().register(program);
		return program;
	}

    public ComputeShaderProgram getComputeProgram(String computeShaderLocation) {
        return getComputeProgram(computeShaderLocation, new Defines());
    }
	public ComputeShaderProgram getComputeProgram(String computeShaderLocation, Defines defines) {
        return GraphicsContext.getInstance().calculate(() -> {
            ComputeShaderProgram program = new ComputeShaderProgram(ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + computeShaderLocation)), defines);
            LOADED_PROGRAMS.add(program);
            Engine.getEventBus().register(program);
            return program;
        });
	}

	public Program getProgram(CodeSource vertexShaderSource, CodeSource fragmentShaderSource, Defines defines) {
		return getProgram(vertexShaderSource, null, fragmentShaderSource, defines);
	}
	public Program getProgram(CodeSource vertexShaderSource, CodeSource geometryShaderSource, CodeSource fragmentShaderSource, Defines defines) {
		return GraphicsContext.getInstance().calculate(() -> {
            Program program = new Program(vertexShaderSource, geometryShaderSource, fragmentShaderSource, defines);
            LOADED_PROGRAMS.add(program);
            Engine.getEventBus().register(program);
            return program;
        });
	}

    public Program getFirstpassDefaultProgram() {
        return FIRSTPASS_DEFAULT_PROGRAM;
    }

    public Program getFirstpassAnimatedDefaultProgram() {
        return FIRSTPASS_ANIMATED_DEFAULT_PROGRAM;
    }

    public VertexShader getDefaultFirstpassVertexShader() {
        try {
            return VertexShader.load(FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE, new Defines());
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Default vertex de.hanno.hpengine.shader cannot be loaded...");
            System.exit(-1);
        }
        return null;
    }

    public ComputeShaderProgram getHighZProgram() {
        return HIGHZ_PROGRAM;
    }

    public Program getAppendDrawCommandProgram() {
        return APPEND_DRAWCOMMANDS_PROGRAM;
    }
}
