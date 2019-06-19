package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.engine.backend.EngineContext;
import de.hanno.hpengine.util.ressources.CodeSource;
import de.hanno.hpengine.util.script.ScriptManager;
import org.jetbrains.annotations.NotNull;

import javax.script.ScriptContext;
import java.util.HashMap;
import java.util.Map;

public class JavaScriptComponent extends BaseComponent implements ScriptComponent {
    String script;
    private ScriptManager scriptManager;
    private ScriptContext context;
    private Map map = new HashMap();

    public JavaScriptComponent(String script, ScriptManager scriptManager) {
        super();
        this.script = script;
        this.scriptManager = scriptManager;
        create(scriptManager);
    }

    private void create(ScriptManager scriptManager) {
        context = scriptManager.createContext();
        scriptManager.evalInit(this);
    }

    @Override
    public String getIdentifier() {
        return "JavaScriptComponent";
    }

    @Override
    public void update(float seconds) {
        scriptManager.evalUpdate(this, seconds);
    }

    public void setInt(String name, int value) {
        context.setAttribute(name, value, ScriptContext.ENGINE_SCOPE);
    }

    public ScriptContext getContext() {
        return context;
    }

    public String getScript() {
        return script;
    }

    public void eval(String script) {
        scriptManager.eval(getContext(), script);
    }

    @Override
    public void reload() {
        create(scriptManager);
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

    @NotNull
    @Override
    public CodeSource getCodeSource() {
        return new CodeSource(script);
    }

    @Override
    public void load() {

    }

    @Override
    public void unload() {

    }
}
