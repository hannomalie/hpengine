package de.hanno.hpengine.engine.scene;

import java.util.Comparator;

import de.hanno.hpengine.engine.model.Transformable;
import org.lwjgl.util.vector.Vector3f;

public class TransformDistanceComparator<T extends Transformable> implements Comparator<T>{

	protected T reference;

	public T getReference() {
		return reference;
	}
	
	@Override
	public int compare(T o1, T o2) {
		if(reference == null) {
			return 0;
		}

		Vector3f distanceToFirst = Vector3f.sub(reference.getPosition(), o1.getPosition(), null);
		Vector3f distanceToSecond = Vector3f.sub(reference.getPosition(), o2.getPosition(), null);
		return Float.compare(distanceToFirst.lengthSquared(), distanceToSecond.lengthSquared());
	}


	public void setReference(T reference) {
		this.reference = reference;
	}

	public TransformDistanceComparator(T reference) {
		this.reference = reference;
	}

}
