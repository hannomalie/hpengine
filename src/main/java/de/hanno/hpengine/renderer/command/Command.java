package de.hanno.hpengine.renderer.command;

import de.hanno.hpengine.engine.Engine;

@FunctionalInterface
public interface Command<T extends Result> {

	T execute(Engine engine);

}
