package de.hanno.hpengine.component;

import de.hanno.hpengine.util.script.ScriptManager;

import javax.script.ScriptContext;

public class JavaScriptComponent extends BaseComponent implements ScriptComponent {
    String script = "";
    private ScriptContext context;

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
}
