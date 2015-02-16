package main.shader;

import static main.log.ConsoleLogger.getLogger;

import java.io.File;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import main.World;
import main.renderer.Renderer;
import main.renderer.Result;
import main.renderer.command.Command;
import main.util.ressources.FileMonitor;
import main.util.ressources.ReloadOnFileChangeListener;
import main.util.ressources.Reloadable;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;


public class ComputeShaderProgram extends AbstractProgram implements Reloadable {
	private static Logger LOGGER = getLogger();
	
	private String computeShaderName;
	private Renderer renderer;
	
	private ReloadOnFileChangeListener<ComputeShaderProgram> reloadOnFileChangeListener;
	private FileAlterationObserver observerShader;

	public ComputeShaderProgram(Renderer renderer, String computeShaderName) {
		this.computeShaderName = computeShaderName;
		this.renderer = renderer;
		
		observerShader = new FileAlterationObserver(Program.getDirectory());
		load();
	}

	@Override
	public void load() {
		clearUniforms();
		int computeShaderId = -1;
		try {
			computeShaderId = Program.loadShader(computeShaderName, GL43.GL_COMPUTE_SHADER);
		} catch (Exception e) {
			e.printStackTrace();
		}
		setId(GL20.glCreateProgram());
		GL20.glAttachShader(id, computeShaderId);
		GL20.glLinkProgram(id);
		GL20.glValidateProgram(id);
		
		addFileListeners();
	}
	

	private void addFileListeners() {
		
		clearListeners();
		reloadOnFileChangeListener = new ReloadOnFileChangeListener<ComputeShaderProgram>(this) {

			@Override
			public boolean shouldReload(File changedFile) {
				String fileName = FilenameUtils.getBaseName(changedFile.getAbsolutePath());
				if(computeShaderName != null && computeShaderName.startsWith(fileName)) {
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
	}

	public void unload() {
		GL20.glDeleteProgram(id);
		setId(-1);
	}
	
	public void reload() {
		final ComputeShaderProgram self = this;
		
		SynchronousQueue<Result> queue = renderer.addCommand(new Command<Result>(){
			@Override
			public Result execute(World world) {
				self.unload();
				self.load();
				return new Result();
			}
		});
		Result result = null;
		try {
			result = queue.poll(5, TimeUnit.MINUTES);
			if (!result.isSuccessful()) {
				System.out.println("Program not reloaded");
			} else {
				System.out.println("Program reloaded");
			}
		} catch (Exception e1) {
			System.out.println("Program not reloaded");
		}
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof ComputeShaderProgram) || other == null) {
			return false;
		}
		
		ComputeShaderProgram otherProgram = (ComputeShaderProgram) other;
		
		if (this.computeShaderName.equals(otherProgram.computeShaderName == null)) {
			return true;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		int hash = 0;
		hash += (computeShaderName != null? computeShaderName.hashCode() : 0);
		return hash;
	};
}
