package main.renderer.command;

import main.World;
import main.renderer.Renderer;
import main.renderer.Result;


public interface Command<T extends Result> {

	T execute(World world);

}
