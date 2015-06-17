package renderer.command;

import engine.World;
import renderer.Result;


public interface Command<T extends Result> {

	T execute(World world);

}
