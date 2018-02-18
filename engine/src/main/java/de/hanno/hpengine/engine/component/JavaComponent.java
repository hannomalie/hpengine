package de.hanno.hpengine.engine.component;

import de.hanno.compiler.RuntimeJavaCompiler;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.util.ressources.CodeSource;
import de.hanno.hpengine.util.ressources.FileMonitor;
import de.hanno.hpengine.util.ressources.ReloadOnFileChangeListener;
import de.hanno.hpengine.util.ressources.Reloadable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class JavaComponent extends BaseComponent implements ScriptComponent, Reloadable {

    public static final String WORKING_DIR = DirectoryManager.WORKDIR_NAME + "/java";
    static {
        File workingDir = new File(WORKING_DIR);
        try {
            if(!workingDir.exists()) {
                Files.createDirectory(workingDir.toPath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static final RuntimeJavaCompiler compiler = new RuntimeJavaCompiler(WORKING_DIR);
    private final CodeSource javaCodeSource;
    private FileAlterationObserver observerJavaFile;
    private ReloadOnFileChangeListener<JavaComponent> reloadOnFileChangeListener;


    private Map map = new HashMap<>();
    private Class<?> compiledClass;
    private boolean isLifeCycle;
    private Object instance;

    public JavaComponent(String sourceCode) {
        this(new CodeSource(sourceCode));
    }

    public JavaComponent(CodeSource codeSource) {
        super();
        this.javaCodeSource = codeSource;
    }

    @Override
    public String getIdentifier() {
        return "JavaComponent";
    }

    @Override
    public void init(Engine engine) {
        observerJavaFile = new FileAlterationObserver(javaCodeSource.isFileBased() ? javaCodeSource.getFile().getParent() : JavaComponent.getDirectory());
        addFileListeners();
        initWrappingComponent();
        super.init(engine);
        if(isLifeCycle) {
            ((LifeCycle) instance).init(engine);
        }
    }

    @Override
    public void update(Engine engine, float seconds) {
        if(isLifeCycle) {
            ((LifeCycle) instance).update(engine, seconds);
        }
    }

    public String getSourceCode() {
        return javaCodeSource.getSource();
    }

    @Override
    public void reload() {
        Reloadable.super.reload();
    }

    @Override
    public String getName() {
        return this.toString();
    }

    @Override
    public Object get(Object key) {
        return map.get(key);
    }

    @Override
    public Object put(Object key, Object value) {
        return map.put(key, value);
    }

    private void initWrappingComponent() {
        try {
            compiledClass = compiler.compile(javaCodeSource.getSource());
            instance = compiledClass.getConstructors()[0].newInstance();
            try {
                Field entityField = instance.getClass().getDeclaredField("entity");
                entityField.set(instance, getEntity());
            } catch (Exception e) {

            }
            isLifeCycle = instance instanceof LifeCycle;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addFileListeners() {

        clearListeners();

        reloadOnFileChangeListener = new ReloadOnFileChangeListener(this) {
            @Override
            public boolean shouldReload(File changedFile) {
                String fileName = FilenameUtils.getBaseName(changedFile.getAbsolutePath());
                return javaCodeSource.isFileBased() && javaCodeSource.getFilename().startsWith(fileName);
            }
        };

        observerJavaFile.addListener(reloadOnFileChangeListener);
        FileMonitor.getInstance().add(observerJavaFile);
    }

    private void clearListeners() {
        if(observerJavaFile != null) {
            observerJavaFile.removeListener(reloadOnFileChangeListener);
        }
    }

    public Class<?> getCompiledClass() {
        return compiledClass;
    }

    public Object getInstance() {
        return instance;
    }

    private static String getDirectory() {
        return DirectoryManager.GAMEDIR_NAME + "/scripts/";
    }

    @Override
    public void load() {
        javaCodeSource.load();
        initWrappingComponent();
    }

    @Override
    public void unload() {

    }
}
