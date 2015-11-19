package component;

import engine.AppContext;
import util.script.ScriptManager;

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
}
