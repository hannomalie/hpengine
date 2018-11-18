package de.hanno.hpengine.engine.graphics.renderer.command;

@FunctionalInterface
public interface Command<T extends Result> {

	T execute();

}
