package de.hanno.hpengine.engine.component;

import de.hanno.hpengine.util.script.ScriptManager;

import javax.script.ScriptContext;
import java.util.HashMap;
import java.util.Map;

public class JavaScriptComponent extends BaseComponent implements ScriptComponent {
    String script = "";
    private ScriptContext context;
    private Map map = new HashMap();

    public JavaScriptComponent(String script) {
        super();
        this.script = script;
    }

    public JavaScriptComponent() {
        super();
    }

    @Override
    public String getIdentifier() {
        return "JavaScriptComponent";
    }

    @Override
    public void init() {
        super.init();
        context = ScriptManager.getInstance().createContext();
        ScriptManager.getInstance().evalInit(this);
    }

    @Override
    public void update(float seconds) {
        ScriptManager.getInstance().evalUpdate(this, seconds);
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
        ScriptManager.getInstance().eval(getContext(), script);
    }

    @Override
    public void reload() {
        init();
    }

    @Override
    public Object get(Object key) {
        return map.get(key);
    }

    @Override
    public Object put(Object key, Object value) {
        return map.put(key, value);
    }
}
