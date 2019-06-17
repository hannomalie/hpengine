package de.hanno.hpengine.engine.component;

import de.hanno.compiler.RuntimeJavaCompiler;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.backend.EngineContext;
import de.hanno.hpengine.engine.directory.GameDirectory;
import de.hanno.hpengine.engine.lifecycle.EngineConsumer;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.util.ressources.CodeSource;
import de.hanno.hpengine.util.ressources.Reloadable;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class JavaComponent extends BaseComponent implements ScriptComponent, Reloadable {

    private static RuntimeJavaCompiler compiler = null;
    RuntimeJavaCompiler getCompiler(GameDirectory gameDirectory) {
        compiler = new RuntimeJavaCompiler(gameDirectory.getJava().getPath());
        return compiler;
    }

    private final CodeSource javaCodeSource;
    private final GameDirectory gameDirectory;

    private Map map = new HashMap<>();
    private Class<?> compiledClass;
    private boolean isLifeCycle;
    private boolean isEngineConsumer;
    private Object instance;

    public JavaComponent(CodeSource codeSource, GameDirectory gameDirectory) {
        super();
        this.javaCodeSource = codeSource;
        this.gameDirectory = gameDirectory;
    }

    @Override
    public String getIdentifier() {
        return "JavaComponent";
    }

    @Override
    public void init(EngineContext engine) {
        initWrappingComponent(engine.getConfig().getDirectories().getGameDir());
        super.init(engine);
        if(isLifeCycle) {
            ((LifeCycle) instance).init(engine);
        }
    }

//    TODO: Make this better
    public void initWithEngine(Engine engine) {
        if(isEngineConsumer) {
            ((EngineConsumer) instance).consume(engine);
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

    private void initWrappingComponent(GameDirectory gameDir) {
        try {
            compiledClass = getCompiler(gameDir).compile(javaCodeSource.getSource());
            instance = compiledClass.getConstructors()[0].newInstance();
            try {
                Field entityField = instance.getClass().getDeclaredField("entity");
                entityField.set(instance, getEntity());
            } catch (Exception e) {

            }
            isLifeCycle = instance instanceof LifeCycle;
            isEngineConsumer= instance instanceof EngineConsumer;

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

    @Override
    public void load() {
        javaCodeSource.load();
        initWrappingComponent(gameDirectory);
    }

    @Override
    public void unload() {

    }

    @NotNull
    @Override
    public CodeSource getCodeSource() {
        return javaCodeSource;
    }
}
