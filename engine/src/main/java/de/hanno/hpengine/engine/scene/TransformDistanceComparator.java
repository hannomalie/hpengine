package de.hanno.hpengine.engine.scene;

import java.util.Comparator;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Transformable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class TransformDistanceComparator<T extends Entity> implements Comparator<T>{

	protected T reference;

	public T getReference() {
		return reference;
	}
	
	@Override
	public int compare(T o1, T o2) {
		if(reference == null) {
			return 0;
		}

		float distanceToFirst = reference.getPosition().distanceSquared(o1.getPosition());
		float distanceToSecond = reference.getPosition().distanceSquared(o2.getPosition());
		return Float.compare(distanceToFirst, distanceToSecond);
	}


	public void setReference(T reference) {
		this.reference = reference;
	}

	public TransformDistanceComparator(T reference) {
		this.reference = reference;
	}

}
