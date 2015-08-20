package util.script;

import component.ScriptComponent;
import engine.World;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;

import javax.script.*;

public class ScriptManager {

	private final ScriptContext globalContext;
	private World world;
	private ScriptEngine engine;
	private DefaultCompletionProvider provider;
	private Bindings globalBindings;

	public ScriptManager(World world) {
		this.world = world;
		NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
		engine = factory.getScriptEngine(new String[] { "--global-per-engine" });
		globalContext = engine.getContext();
		globalBindings = globalContext.getBindings(ScriptContext.ENGINE_SCOPE);
		provider = new DefaultCompletionProvider();
		defineGlobals();
	}
	

	public void eval(String script) throws ScriptException {
		engine.eval(script);
		System.out.println("Script executed...");
	}

	private void defineGlobals() {
		define("world", world);
		define("renderer", world.getRenderer());
		define("entityFactory", world.getEntityFactory());
		define("materialFactory", world.getRenderer().getMaterialFactory());
		define("textureFactory", world.getRenderer().getTextureFactory());
		define("objLoader", world.getRenderer().getOBJLoader());
	}
	
	public void define(String name, Object object) {
//		engine.put(name, object);
		globalContext.getBindings(ScriptContext.ENGINE_SCOPE).put(name, object);
	    provider.addCompletion(new BasicCompletion(provider, name));
	}

	public DefaultCompletionProvider getProvider() {
		return provider;
	}
	public void setProvider(DefaultCompletionProvider provider) {
		this.provider = provider;
	}

	public void evalUpdate(ScriptComponent scriptComponent, float seconds) {
		engine.setContext(scriptComponent.getContext());
		try {
			((Invocable)engine).invokeFunction("update", seconds);
//			engine.eval(String.format("update(%s)", seconds, scriptComponent.getContext()));
		} catch (ScriptException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	public ScriptContext createContext() {
		ScriptContext context = new SimpleScriptContext();
		context.setBindings(world.getScriptManager().getGlobalContext(), ScriptContext.GLOBAL_SCOPE);
		context.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
		return context;
	}

	public Bindings getGlobalContext() {
		return globalBindings;
	}

	public void evalInit(ScriptComponent scriptComponent) {
		try {
			engine.eval(scriptComponent.getScript(), scriptComponent.getContext().getBindings(ScriptContext.ENGINE_SCOPE));

			engine.setContext(scriptComponent.getContext());
			scriptComponent.getContext().getBindings(ScriptContext.ENGINE_SCOPE).put("entity", scriptComponent.getEntity());
			((Invocable)engine).invokeFunction("init", world);

		} catch (ScriptException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
//			e.printStackTrace();
		}
	}

	public void eval(ScriptContext context, String script) {
		try {
			engine.eval(script, context.getBindings(ScriptContext.ENGINE_SCOPE));
		} catch (ScriptException e) {
			e.printStackTrace();
		}
	}
}
