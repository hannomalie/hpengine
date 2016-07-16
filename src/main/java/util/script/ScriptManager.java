package util.script;

import component.JavaScriptComponent;
import engine.AppContext;
import engine.model.EntityFactory;
import engine.model.OBJLoader;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import renderer.Renderer;
import renderer.material.MaterialFactory;
import texture.TextureFactory;

import javax.script.*;
import java.util.logging.Logger;

public class ScriptManager {

    private static final Logger LOGGER = Logger.getLogger(ScriptManager.class.getName());

    private static volatile ScriptManager instance;
    private final ScriptContext globalContext;
	private AppContext appContext;
	private ScriptEngine engine;
	private DefaultCompletionProvider provider;
	private Bindings globalBindings;

	private ScriptManager(AppContext appContext) {
		this.appContext = appContext;
		NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
		engine = factory.getScriptEngine(new String[] { "--global-per-engine" });
		globalContext = engine.getContext();
		globalBindings = globalContext.getBindings(ScriptContext.ENGINE_SCOPE);
		provider = new DefaultCompletionProvider();
	}
	

	public void eval(String script) throws ScriptException {
		engine.eval(script);
		LOGGER.info("Script executed...");
	}

	public void defineGlobals() {
		define("world", appContext);
        define("renderer", Renderer.getInstance());
        define("entityFactory", EntityFactory.getInstance());
		define("materialFactory", MaterialFactory.getInstance());
		define("textureFactory", TextureFactory.getInstance());
		define("objLoader", new OBJLoader());
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

	public void evalUpdate(JavaScriptComponent javaScriptComponent, float seconds) {
		engine.setContext(javaScriptComponent.getContext());
		try {
			((Invocable)engine).invokeFunction("update", seconds);
//			engine.eval(String.format("update(%s)", seconds, javaScriptComponent.getContext()));
		} catch (ScriptException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	public ScriptContext createContext() {
		ScriptContext context = new SimpleScriptContext();
		context.setBindings(appContext.getScriptManager().getGlobalContext(), ScriptContext.GLOBAL_SCOPE);
		context.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
		return context;
	}

	public Bindings getGlobalContext() {
		return globalBindings;
	}

	public void evalInit(JavaScriptComponent javaScriptComponent) {
		try {
			engine.eval(javaScriptComponent.getScript(), javaScriptComponent.getContext().getBindings(ScriptContext.ENGINE_SCOPE));

			engine.setContext(javaScriptComponent.getContext());
			javaScriptComponent.getContext().getBindings(ScriptContext.ENGINE_SCOPE).put("entity", javaScriptComponent.getEntity());
			((Invocable)engine).invokeFunction("init", appContext);

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

    public static ScriptManager getInstance() {
        if(instance == null) {
            instance = new ScriptManager(AppContext.getInstance());
        }
        return instance;
    }
}
