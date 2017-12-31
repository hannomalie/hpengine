package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.DeferredRenderer;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.util.ressources.CodeSource;
import org.apache.commons.io.FileUtils;
import de.hanno.hpengine.engine.graphics.shader.define.Define;
import org.lwjgl.Sys;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
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

            FIRSTPASS_DEFAULT_PROGRAM = getProgram(FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE, FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE);
            FIRSTPASS_ANIMATED_DEFAULT_PROGRAM = getProgram(FIRSTPASS_ANIMATED_DEFAULT_VERTEXSHADER_SOURCE, FIRSTPASS_DEFAULT_FRAGMENTSHADER_SOURCE);

            APPEND_DRAWCOMMANDS_PROGRAM = getProgram("append_drawcommands_vertex.glsl", null);
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

    public Program getProgram(String vertexShaderFilename, String fragmentShaderFileName) throws Exception {
        CodeSource vertexShaderSource = ShaderSourceFactory.getShaderSource(new File(getDirectory() + vertexShaderFilename));
        CodeSource fragmentShaderSource = ShaderSourceFactory.getShaderSource(new File(getDirectory() + fragmentShaderFileName));

        return getProgram(vertexShaderSource, fragmentShaderSource);
    }
    public Program getProgram(CodeSource vertexShaderSource, CodeSource fragmentShaderSource) throws IOException {

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
        return GraphicsContext.getInstance().calculate(() -> {
            ComputeShaderProgram program = new ComputeShaderProgram(ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + computeShaderLocation)), defines);
            LOADED_PROGRAMS.add(program);
            Engine.getEventBus().register(program);
            return program;
        });
	}

	public Program getProgram(boolean needsTextures, CodeSource vertexShaderSource, CodeSource fragmentShaderSource) {
		return getProgram(needsTextures, vertexShaderSource, null, fragmentShaderSource);
	}
	public Program getProgram(boolean needsTextures, CodeSource vertexShaderSource, CodeSource geometryShaderSource, CodeSource fragmentShaderSource) {
		return GraphicsContext.getInstance().calculate(() -> {
            Program program = new Program(vertexShaderSource, geometryShaderSource, fragmentShaderSource, needsTextures, "");
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
            return VertexShader.load(FIRSTPASS_DEFAULT_VERTEXSHADER_SOURCE);
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
