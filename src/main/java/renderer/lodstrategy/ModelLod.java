package renderer.lodstrategy;

import component.ModelComponent;
import org.lwjgl.util.vector.Vector3f;
import renderer.RenderExtract;

public class ModelLod {
    public enum ModelLodStrategy {
        CONSTANT_LEVEL(new ModelLodStrategyImpl() {
            @Override
            public int getIndexBufferIndex(RenderExtract extract, ModelComponent modelComponent) {
                return 0;
            }
        }),
        CONSTANT_LEVEL_1(new ModelLodStrategyImpl() {
            @Override
            public int getIndexBufferIndex(RenderExtract extract, ModelComponent modelComponent) {
                return Math.max(1, modelComponent.getLodLevels().size()-1);
            }
        }),
        CONSTANT_LEVEL_2(new ModelLodStrategyImpl() {
            @Override
            public int getIndexBufferIndex(RenderExtract extract, ModelComponent modelComponent) {
                return Math.max(2, modelComponent.getLodLevels().size()-1);
            }
        }),
        CONSTANT_LEVEL_3(new ModelLodStrategyImpl() {
            @Override
            public int getIndexBufferIndex(RenderExtract extract, ModelComponent modelComponent) {
                return Math.max(3, modelComponent.getLodLevels().size()-1);
            }
        }),
        CONSTANT_LEVEL_4(new ModelLodStrategyImpl() {
            @Override
            public int getIndexBufferIndex(RenderExtract extract, ModelComponent modelComponent) {
                return Math.max(4, modelComponent.getLodLevels().size()-1);
            }
        }),
        CONSTANT_LEVEL_5(new ModelLodStrategyImpl() {
            @Override
            public int getIndexBufferIndex(RenderExtract extract, ModelComponent modelComponent) {
                return Math.max(5, modelComponent.getLodLevels().size()-1);
            }
        }),
        DISTANCE_BASED(new ModelLodStrategyImpl() {
            private Vector3f temp = new Vector3f();
            @Override
            public int getIndexBufferIndex(RenderExtract extract, ModelComponent modelComponent) {
                Vector3f vectorFromTo = Vector3f.sub(extract.camera.getWorldPosition(), modelComponent.getEntity().getCenterWorld(), temp);
                float distanceCenterCamera = vectorFromTo.length();

                if(distanceCenterCamera < modelComponent.getBoundingSphereRadius()) {
                    return 0;
                }

                int maxDistance = 100;
                int distancePerLodLevel = maxDistance / modelComponent.getLodLevels().size();

                int resultingIndex = ((int) distanceCenterCamera) / distancePerLodLevel;
                return Math.min(Math.max(0,resultingIndex-1), modelComponent.getLodLevels().size()-1);
            }
        });

        private final ModelLodStrategyImpl implementation;

        ModelLodStrategy(ModelLodStrategyImpl implementation) {
            this.implementation = implementation;
        }

        public int getIndexBufferIndex(RenderExtract extract, ModelComponent modelComponent) {
            return implementation.getIndexBufferIndex(extract, modelComponent);
        }
    }

}
