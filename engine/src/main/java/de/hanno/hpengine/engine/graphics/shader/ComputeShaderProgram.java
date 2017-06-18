package de.hanno.hpengine.engine.graphics.shader;

import com.sun.org.apache.xpath.internal.operations.Bool;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import de.hanno.hpengine.util.ressources.CodeSource;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import org.lwjgl.util.glu.GLU;
import de.hanno.hpengine.engine.graphics.shader.define.Define;
import de.hanno.hpengine.util.ressources.FileMonitor;
import de.hanno.hpengine.util.ressources.ReloadOnFileChangeListener;
import de.hanno.hpengine.util.ressources.Reloadable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static de.hanno.hpengine.engine.graphics.shader.Shader.getDirectory;


public class ComputeShaderProgram extends AbstractProgram implements Reloadable {
    private static final Logger LOGGER = Logger.getLogger(ComputeShaderProgram.class.getName());
	
	private CodeSource computeShaderSource;
    private ComputeShader computeShader;

	private ReloadOnFileChangeListener<ComputeShaderProgram> reloadOnFileChangeListener;
	private FileAlterationObserver observerShader;

    public ComputeShaderProgram(CodeSource computeShaderSource) {
        this(computeShaderSource, Collections.EMPTY_LIST);
    }
	public ComputeShaderProgram(CodeSource computeShaderSource, List<Define> defines) {
		this.computeShaderSource = computeShaderSource;
        this.defines = defines;

		observerShader = new FileAlterationObserver(getDirectory());
		load();
		addFileListeners();
	}

	@Override
	public void load() {
		clearUniforms();
		try {
            computeShader = ComputeShader.load(computeShaderSource, Define.getStringForDefines(defines));
		} catch (Exception e) {
			e.printStackTrace();
		}
		printIfError("Pre load ");
		printIfError("Create program ");
		attachShader(computeShader);
		printIfError("Attach de.hanno.hpengine.shader ");
		GL20.glLinkProgram(id);
		printIfError("Link program ");
		GL20.glValidateProgram(id);
		printIfError("Validate program ");

		if (GL20.glGetProgrami(getId(), GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
			System.err.println("Could not link de.hanno.hpengine.shader: " + computeShaderSource.getFilename());
			System.err.println(GL20.glGetProgramInfoLog(id, 10000));
		}

		printIfError("ComputeShader load ");
	}

	private void printIfError(String message) {
		int error = GL11.glGetError();
		if(error != GL11.GL_NO_ERROR) {
			LOGGER.severe(message + GLU.gluErrorString(GL11.glGetError()));
		}
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
		FileMonitor.getInstance().add(observerShader);
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

		FutureCallable<Boolean> reloadShaderCallable = new FutureCallable() {
			@Override
			public Boolean execute() throws Exception {
				detachShader(computeShader);
				computeShader.reload();
				self.load();
				return true;
			}
		};
		CompletableFuture<Boolean> future = GraphicsContext.getInstance().execute(reloadShaderCallable);

		try {
			Boolean result = future.get(5, TimeUnit.MINUTES);
			if (result.equals(Boolean.TRUE)) {
				LOGGER.info("Program reloaded");
			} else {
				LOGGER.severe("Program not reloaded");
			}
		} catch (Exception e1) {
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
