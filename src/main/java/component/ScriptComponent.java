package component;

import engine.AppContext;

import javax.script.ScriptContext;

public class ScriptComponent extends BaseComponent {
    String script = "";
    private ScriptContext context;

    public ScriptComponent(String script) {
        super();
        this.script = script;
    }

    public ScriptComponent() {
        super();
    }

    @Override
    public String getIdentifier() {
        return "ScriptComponent";
    }

    @Override
    public void init(AppContext appContext) {
        super.init(appContext);
        context = appContext.getScriptManager().createContext();
        appContext.getScriptManager().evalInit(this);
    }

    @Override
    public void update(float seconds) {
        appContext.getScriptManager().evalUpdate(this, seconds);
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
        appContext.getScriptManager().eval(getContext(), script);
    }
}
