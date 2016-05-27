package renderer.command;

import engine.AppContext;
import renderer.RenderExtract;

@FunctionalInterface
public interface Command<T extends Result> {

	T execute(AppContext appContext);

}
