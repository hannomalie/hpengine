package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.graphics.renderer.GLU;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.util.ressources.CodeSource;
import de.hanno.hpengine.util.ressources.FileMonitor;
import de.hanno.hpengine.util.ressources.ReloadOnFileChangeListener;
import de.hanno.hpengine.util.ressources.Reloadable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

import java.io.File;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;

public class ComputeShaderProgram extends AbstractProgram implements Reloadable {
    private static final Logger LOGGER = Logger.getLogger(ComputeShaderProgram.class.getName());
	private final OpenGlProgramManager programManager;

	private CodeSource computeShaderSource;
    private ComputeShader computeShader;

	private ReloadOnFileChangeListener<ComputeShaderProgram> reloadOnFileChangeListener;
	private FileAlterationObserver observerShader;

	public ComputeShaderProgram(OpenGlProgramManager programManager, CodeSource computeShaderSource) {
        this(programManager, computeShaderSource, new Defines());
    }
	public ComputeShaderProgram(OpenGlProgramManager programManager, CodeSource computeShaderSource, Defines defines) {
        super(programManager.getGpuContext().createProgramId());
        this.programManager = programManager;
        this.computeShaderSource = computeShaderSource;
        this.defines = defines;

		observerShader = new FileAlterationObserver(Shader.directory);
		load();
		addFileListeners();
	}

	@Override
	public void load() {
		clearUniforms();
		computeShader = ComputeShader.load(programManager, computeShaderSource, defines);
		printIfError("ComputeShader load " + computeShaderSource.getName());
		LOGGER.info("Loaded computeshader " + computeShaderSource.getName());
		printIfError("Create program " + computeShaderSource.getName());
		attachShader(computeShader);
		printIfError("Attach shader " + computeShaderSource.getName());
		GL20.glLinkProgram(id);
		printIfError("Link program " + computeShaderSource.getName());
		GL20.glValidateProgram(id);
		printIfError("Validate program " + computeShaderSource.getName());

		if (GL20.glGetProgrami(getId(), GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
			System.err.println("Could not link shader: " + computeShaderSource.getFilename());
			System.err.println(GL20.glGetProgramInfoLog(id, 10000));
		}

		printIfError("ComputeShader load ");
	}

	private boolean printIfError(String text) {
		int error = GL11.glGetError();
		boolean isError = error != GL11.GL_NO_ERROR;
		if(isError) {
			LOGGER.severe(text + " " + GLU.gluErrorString(error));
			LOGGER.info(glGetProgramInfoLog(id));
		}

		return isError;
	}

    private void attachShader(Shader shader) {
        GL20.glAttachShader(getId(), shader.getId());
    }

    private void detachShader(Shader shader) {
        GL20.glDetachShader(getId(), shader.getId());
    }

	private void addFileListeners() {
		
		clearListeners();
		reloadOnFileChangeListener = new ReloadOnFileChangeListener<ComputeShaderProgram>(this) {

			@Override
			public boolean shouldReload(File changedFile) {
				String fileName = FilenameUtils.getBaseName(changedFile.getAbsolutePath());
				if(fileName.startsWith("globals.glsl")) {
					return true;
				}

				if(computeShaderSource.getFilename().startsWith(fileName)) {
					return true;
				}
				return false;
			}
		};
		observerShader.addListener(reloadOnFileChangeListener);
//		TODO: Reimplement somewhere else
//		FileMonitor.getInstance().add(observerShader);
	}

	private void clearListeners() {
		if(observerShader != null) {
			observerShader.removeListener(reloadOnFileChangeListener);
		}
	}

	public void dispatchCompute(int num_groups_x, int num_groups_y, int num_groups_z) {
		GL43.glDispatchCompute(num_groups_x, num_groups_y, num_groups_z);
//		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
	}

	public void unload() {
		GL20.glUseProgram(0);
		GL20.glDeleteProgram(id);
	}
	
	public void reload() {
		final ComputeShaderProgram self = this;

		Boolean result = programManager.getGpuContext().calculate((Callable<Boolean>) () -> {
			detachShader(computeShader);
			computeShader.reload();
			self.load();
			return true;
		});
		if (result.equals(Boolean.TRUE)) {
			LOGGER.info("Program reloaded");
		} else {
			LOGGER.severe("Program not reloaded");
		}
	}

	@Override
	public String getName() {
		return new StringJoiner(", ").add(computeShaderSource.getFilename())
				.toString();
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof ComputeShaderProgram) || other == null) {
			return false;
		}
		
		ComputeShaderProgram otherProgram = (ComputeShaderProgram) other;
		
		if (this.computeShaderSource.equals(otherProgram.computeShaderSource == null) &&
            this.defines.isEmpty()) {
			return true;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		int hash = 0;
		hash += (computeShaderSource != null? computeShaderSource.hashCode() : 0);
        hash += defines.hashCode();
		return hash;
	};
}
