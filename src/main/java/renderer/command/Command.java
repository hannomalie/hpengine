package renderer.command;

import engine.AppContext;

@FunctionalInterface
public interface Command<T extends Result> {

	T execute(AppContext appContext);

}
