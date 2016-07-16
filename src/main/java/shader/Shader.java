package shader;

import engine.AppContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.opengl.*;
import renderer.OpenGLContext;
import util.TypedTuple;
import util.Util;
import util.ressources.Reloadable;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Shader extends Reloadable {

    static final Logger LOGGER = Logger.getLogger(Shader.class.getName());

    static <SHADERTYPE extends Shader> SHADERTYPE loadShader(Class<SHADERTYPE> type, String filename) throws Exception {
        return loadShader(type, new File(getDirectory() + filename), "");
    }

    static <SHADERTYPE extends Shader> SHADERTYPE loadShader(Class<SHADERTYPE> type, File file) throws Exception {
        return loadShader(type, file, "");
    }

    static <SHADERTYPE extends Shader> SHADERTYPE loadShader(Class<SHADERTYPE> type, File file, String mapDefinesString) throws Exception {
        return loadShader(type, new ShaderSource(file), mapDefinesString);
    }

    static <SHADERTYPE extends Shader> SHADERTYPE loadShader(Class<SHADERTYPE> type, ShaderSource shaderSource) {
        return loadShader(type, shaderSource, "");
    }

    static <SHADERTYPE extends Shader> SHADERTYPE loadShader(Class<SHADERTYPE> type, ShaderSource shaderSource, String mapDefinesString) {

        if (shaderSource == null) {
            LOGGER.severe("Shadersource null, returning null shader");
            return null;
        }

        String resultingShaderSource = "#version 430 core \n"
                + mapDefinesString + "\n"
                + ShaderDefine.getGlobalDefinesString() + "\n";

        String findStr = "\n";
        int newlineCount = (resultingShaderSource.split(findStr, -1).length - 1);

        String actualShaderSource = shaderSource.getSource();

        try {
            TypedTuple<String, Integer> tuple = replaceIncludes(actualShaderSource, newlineCount);
            actualShaderSource = tuple.getLeft();
            newlineCount = tuple.getRight();
            resultingShaderSource += actualShaderSource;
        } catch (IOException e) {
            e.printStackTrace();
        }

        SHADERTYPE shader = null;
        try {
            shader = type.newInstance();
            shader.setShaderSource(shaderSource);
        } catch (Exception e) {
            e.printStackTrace();
        }

        final SHADERTYPE finalShader = shader;
        final int[] shaderID = new int[1];
        final SHADERTYPE finalShader1 = shader;
        final String finalResultingShaderSource = resultingShaderSource;
        OpenGLContext.getInstance().execute(() -> {
            shaderID[0] = GL20.glCreateShader(finalShader.getShaderType().glShaderType);
            finalShader1.setId(shaderID[0]);
            GL20.glShaderSource(shaderID[0], finalResultingShaderSource);
            GL20.glCompileShader(shaderID[0]);
        });

        shaderSource.setResultingShaderSource(resultingShaderSource);

        final boolean[] shaderLoadFailed = new boolean[1];
        final int finalNewlineCount = newlineCount;
        OpenGLContext.getInstance().execute(() -> {
            if (GL20.glGetShader(shaderID[0], GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("Could not compile " + type.getSimpleName() + ": " + shaderSource.getFilename());
//			System.err.println("Dynamic code takes " + newlineCount + " lines");
                String shaderInfoLog = GL20.glGetShaderInfoLog(shaderID[0], 10000);
                shaderInfoLog = replaceLineNumbersWithDynamicLinesAdded(shaderInfoLog, finalNewlineCount);
                System.err.println(shaderInfoLog);
                shaderLoadFailed[0] = true;
            }
        });

        if (shaderLoadFailed[0]) {
            throw new ShaderLoadException(shaderSource);
        }

		LOGGER.finer(resultingShaderSource);
        OpenGLContext.exitOnGLError("loadShader");

        return shader;
    }

    void setShaderSource(ShaderSource shaderSource);
    ShaderSource getShaderSource();

    static TypedTuple<String, Integer> replaceIncludes(String shaderFileAsText, int currentNewLineCount) throws IOException {

        Pattern includePattern = Pattern.compile("//include\\((.*)\\)");
        Matcher includeMatcher = includePattern.matcher(shaderFileAsText);

        while (includeMatcher.find()) {
            String filename = includeMatcher.group(1);
            String fileToInclude = FileUtils.readFileToString(new File(getDirectory() + filename));
            currentNewLineCount += Util.countNewLines(fileToInclude);
            shaderFileAsText = shaderFileAsText.replaceAll(String.format("//include\\(%s\\)", filename),
                    fileToInclude);
        }

        return new TypedTuple<>(shaderFileAsText, new Integer(currentNewLineCount));
    }

    static String replaceLineNumbersWithDynamicLinesAdded(String shaderInfoLog, int newlineCount) {

		Pattern loCPattern = Pattern.compile("\\((\\w+)\\) :");
		Matcher loCMatcher = loCPattern.matcher(shaderInfoLog);

		while (loCMatcher.find()) {
			String oldLineNumber = loCMatcher.group(1);
			int newLineNumber = Integer.parseInt(oldLineNumber) - newlineCount;
			shaderInfoLog = shaderInfoLog.replaceAll(String.format("\\(%s\\) :", oldLineNumber), String.format("(%d) :", newLineNumber));
		}

		return shaderInfoLog;
	}

    static String getDirectory() {
		return AppContext.WORKDIR_NAME + "/assets/shaders/";
	}

    void setId(int shaderID);
    int getId();

    OpenGLShader getShaderType();

    enum OpenGLShader {
        VertexShader(GL20.GL_VERTEX_SHADER),
        FragmentShader(GL20.GL_FRAGMENT_SHADER),
        GeometryShader(GL32.GL_GEOMETRY_SHADER),
        ComputeShader(GL43.GL_COMPUTE_SHADER);

        public final int glShaderType;

        OpenGLShader(int glShaderType) {
            this.glShaderType = glShaderType;
        }
    }

    final class ShaderSourceFactory {
        public static ShaderSource getShaderSource(String shaderSource) {
            if("".equals(shaderSource)) {
                return null;
            }
            return new ShaderSource(shaderSource);
        }

        public static ShaderSource getShaderSource(File file) {
            if(file.exists()) {
                try {
                    return new ShaderSource(file);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            return null;
        }
    }

    final class ShaderSource implements Reloadable {
        private String source;
        private final String fileName;
        private final File file;
        private String resultingShaderSource;

        private ShaderSource(String shaderSource) {
            this.source = shaderSource;
            this.fileName = "No corresponding file";
            file = null;
        }

        private ShaderSource(File file) throws IOException {
            this.file = file;
            this.source = FileUtils.readFileToString(file);
            this.fileName = FilenameUtils.getBaseName(file.getName());
        }

        public String getSource() {
            return source;
        }

        public String getFilename() {
            return fileName;
        }

        public void setResultingShaderSource(String resultingShaderSource) {
            this.resultingShaderSource = resultingShaderSource;
        }

        public String getResultingShaderSource() {
            return resultingShaderSource;
        }

        @Override
        public String getName() {
            return fileName;
        }

        @Override
        public void load() {
            try {
                source = FileUtils.readFileToString(file);
            } catch (IOException e) {
                System.err.println("Cannot reload shader file, old one is kept (" + getFilename() + ")");
            }
        }

        @Override
        public void unload() {

        }

        @Override
        public void reload() {
            if(isFileBased()) {
               Reloadable.super.reload();
            }
        }

        private boolean isFileBased() {
            return file != null;
        }

    }

    class ShaderLoadException extends RuntimeException {
        private final ShaderSource shaderSource;

        public ShaderLoadException(ShaderSource shaderSource) {
            this.shaderSource = shaderSource;
        }

        public String toString() {
            return shaderSource.getFilename() + "\n" + shaderSource.getSource();
        }

    }
}
