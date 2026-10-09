package net.vulkanmod.mixin.render.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.vulkanmod.interfaces.ExtendedVertexBuilder;
import net.vulkanmod.interfaces.ModelPartCubeMixed;
import net.vulkanmod.render.model.CubeModel;
import net.vulkanmod.render.vertex.VertexUtil;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(ModelPart.class)
public class ModelPartM {

    @Shadow @Final private List<ModelPart.Cube> cubes;

    /**
     * @author
     * @reason
     */
    @Overwrite
    protected void compile(PoseStack.Pose pose, VertexConsumer vertexConsumer, int i, int j, float r, float g, float b, float a) {
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();
        ExtendedVertexBuilder vertexBuilder = vertexConsumer instanceof ExtendedVertexBuilder extended ? extended : null;
        int packedColor = vertexBuilder != null ? VertexUtil.packColor(r, g, b, a) : 0;
        // One local scratch vector per invocation instead of one per polygon.
        // Keep it local: a mod VertexConsumer may recursively render another model.
        Vector3f transformedNormal = new Vector3f();

        for (ModelPart.Cube cube : this.cubes) {
            ModelPartCubeMixed cubeMixed = (ModelPartCubeMixed)(cube);
            CubeModel cubeModel = cubeMixed.getCubeModel();

            ModelPart.Polygon[] var11 = cubeModel.getPolygons();

            cubeModel.transformVertices(matrix4f);

            for (ModelPart.Polygon polygon : var11) {
                Vector3f vector3f = matrix3f.transform(transformedNormal.set(polygon.normal));
                int packedNormal = VertexUtil.packNormal(vector3f.x(), vector3f.y(), vector3f.z());

                ModelPart.Vertex[] vertices = polygon.vertices;

                for (ModelPart.Vertex vertex : vertices) {

                    Vector3f pos = vertex.pos;
                    if (vertexBuilder != null) {
                        vertexBuilder.vertex(pos.x(), pos.y(), pos.z(), packedColor, vertex.u, vertex.v, j, i, packedNormal);
                    } else {
                        vertexConsumer.vertex(pos.x(), pos.y(), pos.z(), r, g, b, a, vertex.u, vertex.v, j, i,
                                vector3f.x(), vector3f.y(), vector3f.z());
                    }
                }
            }
        }

    }
}
