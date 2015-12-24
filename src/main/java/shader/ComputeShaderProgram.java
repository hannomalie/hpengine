package shader;

import static log.ConsoleLogger.getLogger;
import static shader.Shader.*;

import java.io.File;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import renderer.OpenGLContext;
import util.ressources.FileMonitor;
import util.ressources.ReloadOnFileChangeListener;
import util.ressources.Reloadable;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import org.lwjgl.util.glu.GLU;


public class ComputeShaderProgram extends AbstractProgram implements Reloadable {
	private static Logger LOGGER = getLogger();
	
	private ShaderSource computeShaderSource;
    private ComputeShader computeShader;

	private ReloadOnFileChangeListener<ComputeShaderProgram> reloadOnFileChangeListener;
	private FileAlterationObserver observerShader;

	public ComputeShaderProgram(ShaderSource computeShaderSource) {
		this.computeShaderSource = computeShaderSource;

		observerShader = new FileAlterationObserver(getDirectory());
		load();
		addFileListeners();
	}

	@Override
	public void load() {
		clearUniforms();
		try {
            computeShader = ComputeShader.load(computeShaderSource);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Pre load " + GLU.gluErrorString(GL11.glGetError()));
		System.out.println("Create program " + GLU.gluErrorString(GL11.glGetError()));
		attachShader(computeShader);
		System.out.println("Attach shader " + GLU.gluErrorString(GL11.glGetError()));
		GL20.glLinkProgram(id);
		System.out.println("Link program " + GLU.gluErrorString(GL11.glGetError()));
		GL20.glValidateProgram(id);
		System.out.println("Validate program " + GLU.gluErrorString(GL11.glGetError()));
		
		if (GL20.glGetProgram(getId(), GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
			System.err.println("Could not link shader: " + computeShaderSource.getFilename());
			System.err.println(GL20.glGetProgramInfoLog(id, 10000));
		}
		
		System.out.println("ComputeShader load " + GLU.gluErrorString(GL11.glGetError()));
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

		CompletableFuture<Boolean> future = OpenGLContext.getInstance().execute(() -> {
//			self.unload();
            detachShader(computeShader);
            computeShader.reload();
			self.load();
			return true;
		});

		try {
			Boolean result = future.get(5, TimeUnit.MINUTES);
			if (result.equals(Boolean.TRUE)) {
				System.out.println("Program reloaded");
			} else {
				System.out.println("Program not reloaded");
			}
		} catch (Exception e1) {
			System.out.println("Program not reloaded");
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
		
		if (this.computeShaderSource.equals(otherProgram.computeShaderSource == null)) {
			return true;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		int hash = 0;
		hash += (computeShaderSource != null? computeShaderSource.hashCode() : 0);
		return hash;
	};
}
