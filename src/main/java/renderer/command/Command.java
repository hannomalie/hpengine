package renderer.command;

import engine.AppContext;


public interface Command<T extends Result> {

	T execute(AppContext appContext);

}
