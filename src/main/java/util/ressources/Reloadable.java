package util.ressources;

public interface Reloadable extends Loadable {
	public default void reload() {unload(); load();}
}
