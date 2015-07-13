package component;

import engine.World;

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
    public void init(World world) {
        super.init(world);
        context = world.getScriptManager().createContext();
        world.getScriptManager().evalInit(this);
    }

    @Override
    public void update(float seconds) {
        world.getScriptManager().evalUpdate(this, seconds);
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
        world.getScriptManager().eval(getContext(), script);
    }
}
