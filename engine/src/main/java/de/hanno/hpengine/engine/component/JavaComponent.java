package de.hanno.hpengine.engine.component;

import de.hanno.compiler.RuntimeJavaCompiler;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.directory.GameDirectory;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.lifecycle.EngineConsumer;
import de.hanno.hpengine.engine.lifecycle.Updatable;
import de.hanno.hpengine.util.ressources.FileBasedCodeSource;
import de.hanno.hpengine.util.ressources.Reloadable;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static de.hanno.hpengine.engine.backend.ManagerContextKt.getConfig;

public class JavaComponent extends BaseComponent implements ScriptComponent, Reloadable {

    private static RuntimeJavaCompiler compiler = null;
    RuntimeJavaCompiler getCompiler(GameDirectory gameDirectory) {
        compiler = new RuntimeJavaCompiler(gameDirectory.getJava().getPath());
        return compiler;
    }

    private Engine engine;
    private final FileBasedCodeSource javaCodeSource;
    private final GameDirectory gameDirectory;

    private Map map = new HashMap<>();
    private Class<?> compiledClass;
    private boolean isLifeCycle;
    private boolean isEngineConsumer;
    private Object instance;

    public JavaComponent(Engine engine, FileBasedCodeSource codeSource, GameDirectory gameDirectory) {
        super(new Entity());
        this.engine = engine;
        this.javaCodeSource = codeSource;
        this.gameDirectory = gameDirectory;
        initWrappingComponent(getConfig(engine.getManagerContext()).getDirectories().getGameDir());
    }

    @Override
    public void update(@NotNull CoroutineScope scope, float deltaSeconds) {
        if(isLifeCycle) {
            ((Updatable) instance).update(scope, deltaSeconds);
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
            isLifeCycle = instance instanceof Updatable;
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
    public FileBasedCodeSource getCodeSource() {
        return javaCodeSource;
    }
}
