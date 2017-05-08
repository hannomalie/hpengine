package de.hanno.hpengine.shader;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.renderer.GraphicsContext;
import de.hanno.hpengine.util.ressources.CodeSource;
import org.apache.commons.io.FileUtils;
import org.lwjgl.opengl.*;
import de.hanno.hpengine.util.TypedTuple;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.ressources.Reloadable;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Shader extends Reloadable {

    Logger LOGGER = Logger.getLogger(Shader.class.getName());

    static <SHADERTYPE extends Shader> SHADERTYPE loadShader(Class<SHADERTYPE> type, String filename) throws Exception {
        return loadShader(type, new File(getDirectory() + filename), "");
    }

    static <SHADERTYPE extends Shader> SHADERTYPE loadShader(Class<SHADERTYPE> type, File file) throws Exception {
        return loadShader(type, file, "");
    }

    static <SHADERTYPE extends Shader> SHADERTYPE loadShader(Class<SHADERTYPE> type, File file, String mapDefinesString) throws Exception {
        return loadShader(type, new CodeSource(file), mapDefinesString);
    }

    static <SHADERTYPE extends Shader> SHADERTYPE loadShader(Class<SHADERTYPE> type, CodeSource shaderSource) {
        return loadShader(type, shaderSource, "");
    }

    static <SHADERTYPE extends Shader> SHADERTYPE loadShader(Class<SHADERTYPE> type, CodeSource shaderSource, String mapDefinesString) {

        if (shaderSource == null) {
            LOGGER.severe("Shadersource null, returning null de.hanno.hpengine.shader");
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
        GraphicsContext.getInstance().execute(() -> {
            shaderID[0] = GL20.glCreateShader(finalShader.getShaderType().glShaderType);
            finalShader1.setId(shaderID[0]);
            GL20.glShaderSource(shaderID[0], finalResultingShaderSource);
            GL20.glCompileShader(shaderID[0]);
        });

        shaderSource.setResultingShaderSource(resultingShaderSource);

        final boolean[] shaderLoadFailed = new boolean[1];
        final int finalNewlineCount = newlineCount;
        GraphicsContext.getInstance().execute(() -> {
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
        GraphicsContext.exitOnGLError("loadShader");

        return shader;
    }

    void setShaderSource(CodeSource shaderSource);
    CodeSource getShaderSource();

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
		return Engine.WORKDIR_NAME + "/assets/shaders/";
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
        public static CodeSource getShaderSource(String shaderSource) {
            if("".equals(shaderSource)) {
                return null;
            }
            return new CodeSource(shaderSource);
        }

        public static CodeSource getShaderSource(File file) {
            if(file.exists()) {
                try {
                    return new CodeSource(file);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            return null;
        }
    }

    class ShaderLoadException extends RuntimeException {
        private final CodeSource shaderSource;

        public ShaderLoadException(CodeSource shaderSource) {
            this.shaderSource = shaderSource;
        }

        public String toString() {
            return shaderSource.getFilename() + "\n" + shaderSource.getSource();
        }

    }
}
