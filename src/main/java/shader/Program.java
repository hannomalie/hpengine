package shader;

import static log.ConsoleLogger.getLogger;
import static shader.Shader.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import engine.AppContext;
import engine.model.DataChannels;
import event.GlobalDefineChangedEvent;
import org.lwjgl.util.glu.GLU;
import renderer.OpenGLContext;
import renderer.Renderer;
import util.ressources.FileMonitor;
import util.ressources.ReloadOnFileChangeListener;
import util.ressources.Reloadable;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;

import com.google.common.eventbus.Subscribe;

public class Program extends AbstractProgram implements Reloadable {
	private static Logger LOGGER = getLogger();
	
	private EnumSet<DataChannels> channels;
	private boolean needsTextures = true;

	private Map<String, Object> localDefines = new HashMap<>();
	
	private String geometryShaderName;
	private String vertexShaderName;
	private String fragmentShaderName;

	private String fragmentDefines;

	private ReloadOnFileChangeListener<Program> reloadOnFileChangeListener;

	private FileAlterationObserver observerFragmentShader;

	protected Program(String vertexShaderName, String geometryShaderName, String fragmentShaderName, EnumSet<DataChannels> channels, boolean needsTextures, String fragmentDefines) {
		this.channels = channels;
		this.needsTextures = needsTextures;
		this.fragmentDefines = fragmentDefines;
		
		this.geometryShaderName = geometryShaderName;
		this.vertexShaderName = vertexShaderName;
		this.fragmentShaderName = fragmentShaderName;

		observerFragmentShader = new FileAlterationObserver(getDirectory());

		addFileListeners();
		load();
	}
	
	public void load() {
        OpenGLContext.getInstance().doWithOpenGLContext(() -> {
			clearUniforms();

			try {
                attachShader(VertexShader.load(new ShaderSource(new File(getDirectory() + vertexShaderName))));
			} catch (Exception e) {
				try {
                    attachShader(AppContext.getInstance().getRenderer().getProgramFactory().getDefaultFirstpassVertexShader());
				} catch (Exception e1) {
					System.err.println("Not able to load default vertex shader, so what else could be done...");
				}
			}
			try {
                attachShader(loadShader(FragmentShader.class, new ShaderSource(new File(getDirectory() + fragmentShaderName)), fragmentDefines));
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (geometryShaderName != null && geometryShaderName != "") {
				try {
                    attachShader(loadShader(GeometryShader.class, new ShaderSource(new File(getDirectory() + geometryShaderName))));
					System.out.println("Attach geometryshader " + GLU.gluErrorString(GL11.glGetError()));
				} catch (Exception e) {
					System.out.println("Not able to load geometry shader, so what else could be done...");
				}
			}

			bindShaderAttributeChannels();

			GL20.glLinkProgram(id);
			printError("Link program");
			GL20.glValidateProgram(id);
			printError("Validate program");

//		use(); // CAN CAUSE INVALID OPERATION - TODO: CHECK OUT WHY
			addFileListeners();
		});
	}

    private void attachShader(Shader shader) {
        GL20.glAttachShader(getId(), shader.getId());
    }

    private void printError(String text) {
		System.out.println(text + " " + GLU.gluErrorString(GL11.glGetError()));
	}

	private void addFileListeners() {
		
		clearListeners();

		reloadOnFileChangeListener = new ReloadOnFileChangeListener<Program>(this) {

			@Override
			public boolean shouldReload(File changedFile) {
				String fileName = FilenameUtils.getBaseName(changedFile.getAbsolutePath());
				if(fileName.startsWith("globals")) {
					return true;
				}

				if(fragmentShaderName != null && fragmentShaderName.startsWith(fileName) ||
					vertexShaderName != null && vertexShaderName.startsWith(fileName) ||
					geometryShaderName != null && geometryShaderName.startsWith(fileName)) {
					return true;
				}
				return false;
			}
		};
		observerFragmentShader.addListener(reloadOnFileChangeListener);
		FileMonitor.getInstance().add(observerFragmentShader);
	}

	private void clearListeners() {
		if(observerFragmentShader != null) {
			observerFragmentShader.removeListener(reloadOnFileChangeListener);
		}
	}

	public void unload() {
		GL20.glDeleteProgram(id);
	}
	
	public void reload() {
		final Program self = this;

		CompletableFuture<Boolean> future = OpenGLContext.getInstance().doWithOpenGLContext(() -> {
			self.unload();
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
		return new StringJoiner(", ").add(fragmentShaderName).add(vertexShaderName)
				.toString();
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Program) || other == null) {
			return false;
		}
		
		Program otherProgram = (Program) other;
		
		if (this.channels == otherProgram.channels &&
			this.needsTextures == otherProgram.needsTextures &&
				((this.geometryShaderName == null && otherProgram.geometryShaderName == null) ||
				(this.geometryShaderName == "" && otherProgram.geometryShaderName == "") ||
				(this.geometryShaderName.equals(otherProgram.geometryShaderName))) &&
			this.vertexShaderName.equals(otherProgram.vertexShaderName) &&
			this.fragmentShaderName.equals(otherProgram.fragmentShaderName)) {
			return true;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		int hash = 0;
		hash += (channels != null? channels.hashCode() : 0);
		hash += (geometryShaderName != null? geometryShaderName.hashCode() : 0);
		hash += (vertexShaderName != null? vertexShaderName.hashCode() : 0);
		hash += (fragmentShaderName != null? fragmentShaderName.hashCode() : 0);
		return hash;
	};
	
	private void bindShaderAttributeChannels() {
//		LOGGER.log(Level.INFO, "Binding shader input channels:");
		EnumSet<DataChannels> channels = EnumSet.allOf(DataChannels.class);
		for (DataChannels channel: channels) {
			GL20.glBindAttribLocation(id, channel.getLocation(), channel.getBinding());
//			LOGGER.log(Level.INFO, String.format("Program(%d): Bound GL attribute location for %s with %s", id, channel.getLocation(), channel.getBinding()));
		}
	}
	
	public void delete() {
		GL20.glUseProgram(0);
		GL20.glDeleteProgram(id);
	}

	public int getId() {
		return id;
	}

    public boolean needsTextures() {
		return needsTextures;
	}

    public void addDefine(String name, Object define) {
		localDefines.put(name, define);
	}
	public void removeDefine(String name) {
		localDefines.remove(name);
	}

	@Override
	@Subscribe
	public void handle(GlobalDefineChangedEvent e) {
		reload();
	}

	public String getDefineString() {
		StringBuilder builder = new StringBuilder();

		for (Map.Entry<String, Object> shaderDefine : localDefines.entrySet()) {
			builder.append(getDefineTextForObject(shaderDefine));
			builder.append("\n");
		}
		return builder.toString();
	}

	public static String getDefineTextForObject(Map.Entry<String, Object> define) {
		if(define.getValue() instanceof Boolean) {
			return "const bool " + define.getKey() + " = " + define.getValue().toString() + ";\n";
		} else if(define.getValue() instanceof Integer) {
			return "const int " + define.getKey() + " = " + define.getValue().toString() + ";\n";
		} else if(define.getValue() instanceof Float) {
			return "const float " + define.getKey() + " = " + define.getValue().toString() + ";\n";
		} else {
			Logger.getGlobal().info("Local define not supported type for " + define.getKey() + " - " + define.getValue());
			return "";
		}
	}

}
