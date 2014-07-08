package main.util.script;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import main.World;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;

public class ScriptManager {
	
	private World world;
	private ScriptEngine engine;
	private DefaultCompletionProvider provider;

	public ScriptManager(World world) {
		this.world = world;
		engine = new ScriptEngineManager().getEngineByName("nashorn");
		provider = new DefaultCompletionProvider();
		defineGlobals();
	}
	

	public void eval(String script) throws ScriptException {
		engine.eval(script);
	}

	private void defineGlobals() {
		define("world", world);
		define("renderer", world.getRenderer());
		define("entityFactory", world.getRenderer().getEntityFactory());
		define("materialFactory", world.getRenderer().getMaterialFactory());
		define("textureFactory", world.getRenderer().getTextureFactory());
		define("objLoader", world.getRenderer().getOBJLoader());
	}
	
	public void define(String name, Object object) {
		engine.put(name, object);
	    provider.addCompletion(new BasicCompletion(provider, name));
	}

	public DefaultCompletionProvider getProvider() {
		return provider;
	}
	public void setProvider(DefaultCompletionProvider provider) {
		this.provider = provider;
	}
}
