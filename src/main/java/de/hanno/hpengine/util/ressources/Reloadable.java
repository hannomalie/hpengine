package de.hanno.hpengine.util.ressources;

public interface Reloadable extends Loadable {
	default void reload() {unload(); load();}

	String getName();
}