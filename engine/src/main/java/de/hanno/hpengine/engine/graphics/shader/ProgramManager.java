package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.manager.Manager;
import de.hanno.hpengine.util.ressources.CodeSource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.hanno.hpengine.engine.graphics.shader.Shader.*;
import static de.hanno.hpengine.engine.graphics.shader.Shader.ShaderSourceFactory.getShaderSource;

public class ProgramManager implements Manager {

    private CodeSource firstpassDefaultVertexshaderSource;
    private CodeSource firstpassAnimatedDefaultVertexshaderSource;
    private CodeSource firstpassDefaultFragmentshaderSource;

    private Program firstpassDefaultProgram;
    private Program firstpassAnimatedDefaultProgram;

    private ComputeShaderProgram highZProgram;
    private Program appendDrawcommandsProgram;

    private Program renderToQuadProgram;
    private Program debugFrameProgram;
    private Program blurProgram;
    private Program bilateralBlurProgram;
    private Program linesProgram;

	public static List<AbstractProgram> LOADED_PROGRAMS = new CopyOnWriteArrayList<>();
    private Engine engine;
    private GpuContext gpuContext;

    public ProgramManager(Engine engine) {
        this.engine = engine;
        gpuContext = engine.getGpuContext();
        try {
            firstpassAnimatedDefaultVertexshaderSource = getShaderSource(new File(getDirectory() + "first_pass_animated_vertex.glsl"));
            firstpassDefaultVertexshaderSource = getShaderSource(new File(getDirectory() + "first_pass_vertex.glsl"));
            firstpassDefaultFragmentshaderSource = getShaderSource(new File(getDirectory() + "first_pass_fragment.glsl"));

            firstpassDefaultProgram = getProgram(firstpassDefaultVertexshaderSource, firstpassDefaultFragmentshaderSource, new Defines());
            firstpassAnimatedDefaultProgram = getProgram(firstpassAnimatedDefaultVertexshaderSource, firstpassDefaultFragmentshaderSource, new Defines());

            appendDrawcommandsProgram = getProgramFromFileNames("append_drawcommands_vertex.glsl", null, new Defines());
            highZProgram = getComputeProgram("highZ_compute.glsl");

            renderToQuadProgram = getProgram(getShaderSource(new File(Shader.getDirectory() + "passthrough_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "simpletexture_fragment.glsl")), new Defines());
            debugFrameProgram = getProgram(getShaderSource(new File(Shader.getDirectory() + "passthrough_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "debugframe_fragment.glsl")), new Defines());
            blurProgram = getProgram(getShaderSource(new File(Shader.getDirectory() + "passthrough_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "blur_fragment.glsl")), new Defines());
            bilateralBlurProgram = getProgram(getShaderSource(new File(Shader.getDirectory() + "passthrough_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "blur_bilateral_fragment.glsl")), new Defines());
            linesProgram = getProgramFromFileNames("mvp_vertex.glsl", "simple_color_fragment.glsl", new Defines());


        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Not able to load default vertex and fragment shader sources...");
            System.exit(-1);
        }
    }

    public Program getProgramFromFileNames(String vertexShaderFilename, String fragmentShaderFileName, Defines defines) throws Exception {
        CodeSource vertexShaderSource = getShaderSource(new File(getDirectory() + vertexShaderFilename));
        CodeSource fragmentShaderSource = getShaderSource(new File(getDirectory() + fragmentShaderFileName));

        return getProgram(vertexShaderSource, fragmentShaderSource, defines);
    }
	public Program getProgram(Defines defines) {
		Program program = new Program(this, firstpassDefaultVertexshaderSource, null, firstpassDefaultFragmentshaderSource, defines);
		LOADED_PROGRAMS.add(program);
		engine.getEventBus().register(program);
		return program;
	}

    public ComputeShaderProgram getComputeProgram(String computeShaderLocation) {
        return getComputeProgram(computeShaderLocation, new Defines());
    }
	public ComputeShaderProgram getComputeProgram(String computeShaderLocation, Defines defines) {
        return engine.getGpuContext().calculate(() -> {
            ComputeShaderProgram program = new ComputeShaderProgram(this, getShaderSource(new File(Shader.getDirectory() + computeShaderLocation)), defines);
            LOADED_PROGRAMS.add(program);
            engine.getEventBus().register(program);
            return program;
        });
	}

	public Program getProgram(CodeSource vertexShaderSource, CodeSource fragmentShaderSource, Defines defines) {
		return getProgram(vertexShaderSource, null, fragmentShaderSource, defines);
	}
	public Program getProgram(CodeSource vertexShaderSource, CodeSource geometryShaderSource, CodeSource fragmentShaderSource, Defines defines) {
        return engine.getGpuContext().calculate(() -> {
            Program program = new Program(this, vertexShaderSource, geometryShaderSource, fragmentShaderSource, defines);
            LOADED_PROGRAMS.add(program);
            engine.getEventBus().register(program);
            return program;
        });
	}

    public Program getFirstpassDefaultProgram() {
        return firstpassDefaultProgram;
    }

    public Program getFirstpassAnimatedDefaultProgram() {
        return firstpassAnimatedDefaultProgram;
    }

    public VertexShader getDefaultFirstpassVertexShader() {
        try {
            return VertexShader.load(this, firstpassDefaultVertexshaderSource, new Defines());
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Default vertex de.hanno.hpengine.shader cannot be loaded...");
            System.exit(-1);
        }
        return null;
    }

    public ComputeShaderProgram getHighZProgram() {
        return highZProgram;
    }

    public CodeSource getFirstpassDefaultVertexshaderSource() {
        return firstpassDefaultVertexshaderSource;
    }

    public CodeSource getFirstpassAnimatedDefaultVertexshaderSource() {
        return firstpassAnimatedDefaultVertexshaderSource;
    }

    public CodeSource getFirstpassDefaultFragmentshaderSource() {
        return firstpassDefaultFragmentshaderSource;
    }

    public Program getAppendDrawcommandsProgram() {
        return appendDrawcommandsProgram;
    }

    public Program getRenderToQuadProgram() {
        return renderToQuadProgram;
    }

    public Program getDebugFrameProgram() {
        return debugFrameProgram;
    }

    public Program getBlurProgram() {
        return blurProgram;
    }

    public Program getBilateralBlurProgram() {
        return bilateralBlurProgram;
    }

    public Program getLinesProgram() {
        return linesProgram;
    }

    public Program getAppendDrawCommandProgram() {
        return appendDrawcommandsProgram;
    }

    @NotNull
    public Engine getEngine() {
        return engine;
    }

    public GpuContext getGpuContext() {
        return gpuContext;
    }

    @Override
    public void update(float deltaSeconds) {

    }

    @Override
    public void clear() {

    }

    @Override
    public void onEntityAdded(@NotNull List<? extends Entity> entities) {

    }

    @Override
    public void afterUpdate(float deltaSeconds) {

    }
}
