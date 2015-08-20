package renderer.command;

import engine.World;


public interface Command<T extends Result> {

	T execute(World world);

}
