package de.hanno.hpengine.engine.graphics.shader;

import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.backend.OpenGl;
import de.hanno.hpengine.engine.event.GlobalDefineChangedEvent;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.GLU;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.util.ressources.CodeSource;
import de.hanno.hpengine.util.ressources.FileMonitor;
import de.hanno.hpengine.util.ressources.ReloadOnFileChangeListener;
import de.hanno.hpengine.util.ressources.Reloadable;
import net.engio.mbassy.listener.Handler;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import static de.hanno.hpengine.log.ConsoleLogger.getLogger;
import static org.lwjgl.opengl.GL20.*;

public class Program extends AbstractProgram implements Reloadable {
	private final GpuContext<OpenGl> gpuContext;
	private static Logger LOGGER = getLogger();
	
	private boolean needsTextures = true;

	private Map<String, Object> localDefines = new HashMap<>();
	
	private CodeSource geometryShaderSource;
	private CodeSource vertexShaderSource;
	private CodeSource fragmentShaderSource;

    private VertexShader vertexShader;
    private GeometryShader geometryShader;
    private FragmentShader fragmentShader;

	private ReloadOnFileChangeListener<Program> reloadOnFileChangeListener;

	private FileAlterationObserver observerFragmentShader;
	private OpenGlProgramManager programManager;

	protected Program(OpenGlProgramManager programManager, CodeSource vertexShaderSource, CodeSource geometryShaderSource, CodeSource fragmentShaderSource,
                      Defines defines) {
        super(programManager.getGpuContext().createProgramId());
        this.programManager = programManager;
        this.gpuContext = programManager.getGpuContext();
        this.defines = defines;
		
		this.geometryShaderSource = geometryShaderSource;
		this.vertexShaderSource = vertexShaderSource;
		this.fragmentShaderSource = fragmentShaderSource;

		observerFragmentShader = new FileAlterationObserver(Shader.directory);

		addFileListeners();
		load();
	}

	public void load() {
        gpuContext.execute(() -> {
			clearUniforms();

			try {
				vertexShader = programManager.loadShader(VertexShader.class, vertexShaderSource, defines);
			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
				if(fragmentShaderSource != null) {
					fragmentShader = programManager.loadShader(FragmentShader.class, fragmentShaderSource, defines);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (geometryShaderSource != null) {
				try {
                    geometryShader = programManager.loadShader(GeometryShader.class, geometryShaderSource, defines);
					gpuContext.getBackend().getGpuContext().exceptionOnError();
				} catch (Exception e) {
					LOGGER.severe("Not able to load geometry shader, so what else could be done...");
				}
			}

			gpuContext.getBackend().getGpuContext().exceptionOnError();
            attachShader(vertexShader);
            if(fragmentShader != null) attachShader(fragmentShader);
            if(geometryShader != null) attachShader(geometryShader);
			bindShaderAttributeChannels();
			gpuContext.getBackend().getGpuContext().exceptionOnError();

			linkProgram();
			validateProgram();

			gpuContext.getBackend().getGpuContext().exceptionOnError();

			addFileListeners();
		});
	}

	private void validateProgram() {
		GL20.glValidateProgram(id);
		int validationResult = GL20.glGetProgrami(id, GL_VALIDATE_STATUS);
		if(GL_FALSE == validationResult) {
			System.err.println(GL20.glGetProgramInfoLog(id));
			throw new IllegalStateException("Program invalid: " + getName());
		}
	}

	private void linkProgram() {
		GL20.glLinkProgram(id);
		int linkResult = GL20.glGetProgrami(id, GL_LINK_STATUS);
		if(GL_FALSE == linkResult) {
			System.err.println(GL20.glGetProgramInfoLog(id));
			throw new IllegalStateException("Program not linked: " + getName());
		}
	}

	private void attachShader(Shader shader) {
        GL20.glAttachShader(getId(), shader.getId());
		gpuContext.getBackend().getGpuContext().exceptionOnError(shader.getName());

    }
    private void detachShader(Shader shader) {
        GL20.glDetachShader(getId(), shader.getId());
    }

    private boolean printError(String text) {
		int error = GL11.glGetError();
		boolean isError = error != GL11.GL_NO_ERROR;
		if(isError) {
			LOGGER.severe(text + " " + GLU.gluErrorString(error));
			LOGGER.info(glGetProgramInfoLog(id));
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
//		TODO: Reimplement somewhere else
//		FileMonitor.getInstance().add(observerFragmentShader);
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

		Boolean result = gpuContext.calculate((Callable<Boolean>) () -> {
			detachShader(vertexShader);
			if (fragmentShader != null) {
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
		});

		if (result.equals(Boolean.TRUE)) {
			LOGGER.info("Program reloaded");
		} else {
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
		
		if (((this.geometryShaderSource == null && otherProgram.geometryShaderSource == null) ||
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
        hash += defines != null ? defines.hashCode() : 0;
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
