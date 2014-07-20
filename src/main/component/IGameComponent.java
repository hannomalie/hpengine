package main.component;

public interface IGameComponent {
	
	public enum ComponentIdentifier {
		Physic
	}
	
	default public void update(float seconds) {}; 

}
