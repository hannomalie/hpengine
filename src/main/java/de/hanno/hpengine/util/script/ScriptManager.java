package de.hanno.hpengine.util.script;

import de.hanno.hpengine.component.JavaScriptComponent;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.EntityFactory;
import de.hanno.hpengine.engine.model.OBJLoader;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import de.hanno.hpengine.texture.TextureFactory;

import javax.script.*;
import java.util.logging.Logger;

public class ScriptManager {

    private static final Logger LOGGER = Logger.getLogger(ScriptManager.class.getName());

    private static volatile ScriptManager instance;
    private final ScriptContext globalContext;
	private Engine engine;
	private ScriptEngine scriptEngine;
	private DefaultCompletionProvider provider;
	private Bindings globalBindings;

	private ScriptManager(Engine engine) {
		this.engine = engine;
		NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
		this.scriptEngine = factory.getScriptEngine(new String[] { "--global-per-engine" });
		globalContext = this.scriptEngine.getContext();
		globalBindings = globalContext.getBindings(ScriptContext.ENGINE_SCOPE);
		provider = new DefaultCompletionProvider();
	}
	

	public void eval(String script) throws ScriptException {
		scriptEngine.eval(script);
		LOGGER.info("Script executed...");
	}

	public void defineGlobals() {
		define("world", scriptEngine);
        define("renderer", Renderer.getInstance());
        define("entityFactory", EntityFactory.getInstance());
		define("materialFactory", MaterialFactory.getInstance());
		define("textureFactory", TextureFactory.getInstance());
		define("objLoader", new OBJLoader());
	}
	
	public void define(String name, Object object) {
//		scriptEngine.put(name, object);
		globalContext.getBindings(ScriptContext.ENGINE_SCOPE).put(name, object);
	    provider.addCompletion(new BasicCompletion(provider, name));
	}

	public DefaultCompletionProvider getProvider() {
		return provider;
	}
	public void setProvider(DefaultCompletionProvider provider) {
		this.provider = provider;
	}

	public void evalUpdate(JavaScriptComponent javaScriptComponent, float seconds) {
		scriptEngine.setContext(javaScriptComponent.getContext());
		try {
			((Invocable) scriptEngine).invokeFunction("update", seconds);
//			scriptEngine.eval(String.format("update(%s)", seconds, javaScriptComponent.getContext()));
		} catch (ScriptException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	public ScriptContext createContext() {
		ScriptContext context = new SimpleScriptContext();
		context.setBindings(engine.getScriptManager().getGlobalContext(), ScriptContext.GLOBAL_SCOPE);
		context.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
		return context;
	}

	public Bindings getGlobalContext() {
		return globalBindings;
	}

	public void evalInit(JavaScriptComponent javaScriptComponent) {
		try {
			scriptEngine.eval(javaScriptComponent.getScript(), javaScriptComponent.getContext().getBindings(ScriptContext.ENGINE_SCOPE));

			scriptEngine.setContext(javaScriptComponent.getContext());
			javaScriptComponent.getContext().getBindings(ScriptContext.ENGINE_SCOPE).put("entity", javaScriptComponent.getEntity());
			((Invocable) scriptEngine).invokeFunction("init", scriptEngine);

		} catch (ScriptException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
//			e.printStackTrace();
		}
	}

	public void eval(ScriptContext context, String script) {
		try {
			scriptEngine.eval(script, context.getBindings(ScriptContext.ENGINE_SCOPE));
		} catch (ScriptException e) {
			e.printStackTrace();
		}
	}

    public static ScriptManager getInstance() {
        if(instance == null) {
            instance = new ScriptManager(Engine.getInstance());
        }
        return instance;
    }
}
