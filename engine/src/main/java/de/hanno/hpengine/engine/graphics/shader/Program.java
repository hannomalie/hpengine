package de.hanno.hpengine.engine.graphics.shader;

import static de.hanno.hpengine.log.ConsoleLogger.getLogger;
import static de.hanno.hpengine.engine.graphics.shader.Shader.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.event.GlobalDefineChangedEvent;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import de.hanno.hpengine.util.ressources.CodeSource;
import net.engio.mbassy.listener.Handler;
import de.hanno.hpengine.engine.graphics.renderer.GLU;
import de.hanno.hpengine.util.ressources.FileMonitor;
import de.hanno.hpengine.util.ressources.ReloadOnFileChangeListener;
import de.hanno.hpengine.util.ressources.Reloadable;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.google.common.eventbus.Subscribe;

public class Program extends AbstractProgram implements Reloadable {
	private static Logger LOGGER = getLogger();
	
	private boolean needsTextures = true;

	private Map<String, Object> localDefines = new HashMap<>();
	
	private CodeSource geometryShaderSource;
	private CodeSource vertexShaderSource;
	private CodeSource fragmentShaderSource;

    private VertexShader vertexShader;
    private GeometryShader geometryShader;
    private FragmentShader fragmentShader;

	private String fragmentDefines;

	private ReloadOnFileChangeListener<Program> reloadOnFileChangeListener;

	private FileAlterationObserver observerFragmentShader;

	protected Program(CodeSource vertexShaderSource, CodeSource geometryShaderSource, CodeSource fragmentShaderSource,
					  boolean needsTextures, String fragmentDefines) {
		this.needsTextures = needsTextures;
		this.fragmentDefines = fragmentDefines;
		
		this.geometryShaderSource = geometryShaderSource;
		this.vertexShaderSource = vertexShaderSource;
		this.fragmentShaderSource = fragmentShaderSource;

		observerFragmentShader = new FileAlterationObserver(getDirectory());

		addFileListeners();
		load();
	}
	
	public void load() {
        GraphicsContext.getInstance().execute(() -> {
			clearUniforms();

			try {
                vertexShader = VertexShader.load(vertexShaderSource);
			} catch (Exception e) {
				try {
                    vertexShader = ProgramFactory.getInstance().getDefaultFirstpassVertexShader();
				} catch (Exception e1) {
					System.err.println("Not able to load default vertex de.hanno.hpengine.shader, so what else could be done...");
				}
			}
			try {
				if(fragmentShaderSource != null) {
					fragmentShader = loadShader(FragmentShader.class, fragmentShaderSource, fragmentDefines);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (geometryShaderSource != null) {
				try {
                    geometryShader = loadShader(GeometryShader.class, geometryShaderSource);
					printError("Attach geometryshader ");
				} catch (Exception e) {
					LOGGER.severe("Not able to load geometry de.hanno.hpengine.shader, so what else could be done...");
				}
			}

            attachShader(vertexShader);
			if(printError("Attach shader")) {
				throw new RuntimeException("Attach shader failed for " + vertexShader.getName());
			}
            if(fragmentShader != null) attachShader(fragmentShader);
            if(geometryShader != null) attachShader(geometryShader);
			bindShaderAttributeChannels();

			GL20.glLinkProgram(id);
			if(printError("Link program")) {
				throw new RuntimeException("Linking failed");
			}
			GL20.glValidateProgram(id);
			printError("Validate program");

//		use(); // CAN CAUSE INVALID OPERATION - TODO: CHECK OUT WHY
			addFileListeners();
		});
	}

    private void attachShader(Shader shader) {
        GL20.glAttachShader(getId(), shader.getId());
    }
    private void detachShader(Shader shader) {
        GL20.glDetachShader(getId(), shader.getId());
    }

    private boolean printError(String text) {
		int error = GL11.glGetError();
		boolean isError = error != GL11.GL_NO_ERROR;
		if(isError) {
			LOGGER.severe(text + " " + GLU.gluErrorString(error));
		}
		return isError;
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

				if(fragmentShaderSource != null && fragmentShaderSource.getFilename().startsWith(fileName) ||
					vertexShaderSource != null && vertexShaderSource.getFilename().startsWith(fileName) ||
					geometryShaderSource != null && geometryShaderSource.getFilename().startsWith(fileName)) {
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

		CompletableFuture<Boolean> future = GraphicsContext.getInstance().execute(new FutureCallable() {
            @Override
            public Boolean execute() throws Exception {
				detachShader(vertexShader);
				if(fragmentShader != null) {
					detachShader(fragmentShader);
					fragmentShader.reload();
				}
				if (geometryShader != null) {
					detachShader(geometryShader);
					geometryShader.reload();
				}
				vertexShader.reload();
				self.load();
				return true;
			}
        });
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
		return new StringJoiner(", ").add(fragmentShaderSource != null ? fragmentShaderSource.getFilename() : "").add(vertexShaderSource.getFilename())
				.toString();
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Program) || other == null) {
			return false;
		}
		
		Program otherProgram = (Program) other;
		
		if (this.needsTextures == otherProgram.needsTextures &&
				((this.geometryShaderSource == null && otherProgram.geometryShaderSource == null) ||
				(this.geometryShaderSource.equals(otherProgram.geometryShaderSource))) &&
			this.vertexShaderSource.equals(otherProgram.vertexShaderSource) &&
			this.fragmentShaderSource.equals(otherProgram.fragmentShaderSource) &&
            this.defines.isEmpty()) {
			return true;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		int hash = 0;
		hash += (geometryShaderSource != null? geometryShaderSource.hashCode() : 0);
		hash += (vertexShaderSource != null? vertexShaderSource.hashCode() : 0);
		hash += (fragmentShaderSource != null? fragmentShaderSource.hashCode() : 0);
        hash += defines.hashCode();
		return hash;
	};
	
	private void bindShaderAttributeChannels() {
//		LOGGER.de.hanno.hpengine.log(Level.INFO, "Binding de.hanno.hpengine.shader input channels:");
		EnumSet<DataChannels> channels = EnumSet.allOf(DataChannels.class);
		for (DataChannels channel: channels) {
			GL20.glBindAttribLocation(id, channel.getLocation(), channel.getBinding());
//			LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("Program(%d): Bound GL attribute location for %s with %s", id, channel.getLocation(), channel.getBinding()));
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
    @Handler
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
