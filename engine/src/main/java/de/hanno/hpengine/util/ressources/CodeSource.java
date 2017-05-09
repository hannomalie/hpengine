package de.hanno.hpengine.util.ressources;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;

public final class CodeSource implements Reloadable {
    private String source;
    private final String fileName;
    private final File file;
    private String resultingShaderSource;

    public CodeSource(String shaderSource) {
        this.source = shaderSource;
        this.fileName = "No corresponding file";
        file = null;
    }

    public CodeSource(File file) throws IOException {
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
        if (isFileBased()) {
            Reloadable.super.reload();
        }
    }

    public boolean isFileBased() {
        return file != null;
    }

}
