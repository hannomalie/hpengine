package de.hanno.hpengine.component;

import de.hanno.compiler.RuntimeJavaCompiler;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.util.ressources.CodeSource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class JavaComponent extends BaseComponent implements ScriptComponent {

    public static final String WORKING_DIR = Engine.WORKDIR_NAME + "/java";
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
    public void init() {
        if(!isInitialized()) {
            initWrappingComponent();
        }
        if(isLifeCycle) {
            ((LifeCycle) instance).init();
        }
    }

    @Override
    public void update(float seconds) {
        if(isLifeCycle) {
            ((LifeCycle) instance).update(seconds);
        }
    }

    public String getSourceCode() {
        return javaCodeSource.getSource();
    }

    @Override
    public void reload() {
        initWrappingComponent();
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
        super.init();
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

    public Class<?> getCompiledClass() {
        return compiledClass;
    }

    public Object getInstance() {
        return instance;
    }

}
