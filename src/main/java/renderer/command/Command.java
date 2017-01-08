package renderer.command;

import engine.Engine;

@FunctionalInterface
public interface Command<T extends Result> {

	T execute(Engine engine);

}
