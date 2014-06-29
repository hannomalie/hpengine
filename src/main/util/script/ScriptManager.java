package main.util.script;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import main.World;

public class ScriptManager {
	
	private World world;
	private ScriptEngine engine;

	public ScriptManager(World world) {
		this.world = world;
		engine = new ScriptEngineManager().getEngineByName("nashorn");
	}
	
	public void eval(String script) throws ScriptException {
		engine.eval(script);
	}

}
