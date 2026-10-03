package net.vulkanmod.render.instancing;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.Initializer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Runs against the actual optional Forge 0.6.11-13 fixture, while Backend.isOn stays false. */
public final class LegacyFlywheelSmokeTest {
    private LegacyFlywheelSmokeTest() {}

    public static void verify(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> modelClass = Class.forName("com.jozufozu.flywheel.core.model.BlockModel",false,loader);
        Class<?> api = Class.forName("com.jozufozu.flywheel.core.model.Model",false,loader);
        Class<?> readerApi = Class.forName("com.jozufozu.flywheel.api.vertex.VertexList",false,loader);
        var constructor = modelClass.getConstructor(ByteBuffer.class, ByteBuffer.class, BufferBuilder.DrawState.class, int.class, String.class);
        ByteBuffer vertices = MemoryUtil.memAlloc(128), indices = MemoryUtil.memAlloc(24);
        try {
            for(int v=0; v<4; v++) {
                int at=v*32;
                vertices.putFloat(at,v); vertices.putFloat(at+4,2); vertices.putFloat(at+8,3);
                vertices.putInt(at+12,-1); vertices.putFloat(at+16,0.25f); vertices.putFloat(at+20,0.75f);
                vertices.putInt(at+24,0x00F00070);
                vertices.put(at+28,(byte)0); vertices.put(at+29,(byte)127); vertices.put(at+30,(byte)0);
            }
            int[] expected = {0,3,2,2,1,0};
            for(int value : expected) indices.putInt(value);
            indices.flip();
            for(boolean sequential : new boolean[]{true,false}) {
                var state = new BufferBuilder.DrawState(DefaultVertexFormat.BLOCK,4,6,VertexFormat.Mode.QUADS,
                        VertexFormat.IndexType.INT,false,sequential);
                Object model = constructor.newInstance(vertices,indices,state,2,"vulkanmod CPU import oracle");
                try {
                    var geometry = LegacyFlywheelModel.importModel(model);
                    if(geometry.vertexCount()!=4 || geometry.indexCount()!=6 || geometry.isShaded(2)
                            || !geometry.isShaded(1) || geometry.vertices().getFloat(32)!=1
                            || geometry.vertices().getInt(24)!=0x00F00070)
                        throw new AssertionError("Actual Flywheel numeric/shading import changed");
                    int[] actual = new int[6]; var input=geometry.indices();
                    for(int i=0;i<6;i++) actual[i]=Short.toUnsignedInt(input.getShort());
                    if(!Arrays.equals(actual,sequential ? ModelGeometry.quadIndices(4) : expected))
                        throw new AssertionError("Actual Flywheel custom indices changed");
                } finally {
                    // CPU fixture ownership only: model.delete() would call GL even without an EBO.
                    readerApi.getMethod("delete").invoke(api.getMethod("getReader").invoke(model));
                    if(!sequential) {
                        var supplierField=modelClass.getDeclaredField("eboSupplier"); supplierField.setAccessible(true);
                        Object supplier=supplierField.get(model);
                        var indexField=supplier.getClass().getDeclaredField("indexBuffer"); indexField.setAccessible(true);
                        MemoryUtil.memFree((ByteBuffer)indexField.get(supplier));
                    }
                }
            }
        } finally { MemoryUtil.memFree(vertices); MemoryUtil.memFree(indices); }
        try(var first=new LegacyFlywheelInstances(loader); var second=new LegacyFlywheelInstances(loader)) {
            Object data=first.createInstance(); Class<?> type=data.getClass();
            type.getField("blockLight").setByte(data,(byte)15); type.getField("skyLight").setByte(data,(byte)7);
            type.getField("r").setByte(data,(byte)128);
            ((Matrix4f)type.getField("model").get(data)).translation(12,34,56);
            type.getMethod("markDirty").invoke(data);
            var snapshot=first.snapshot();
            if(Byte.toUnsignedInt(snapshot.get(0))!=240 || Byte.toUnsignedInt(snapshot.get(1))!=112
                    || Byte.toUnsignedInt(snapshot.get(4))!=128 || snapshot.getFloat(56)!=12
                    || snapshot.getFloat(60)!=34 || snapshot.getFloat(64)!=56 || snapshot.getFloat(72)!=1)
                throw new AssertionError("Legacy transformed record reencoding changed");
            if(!snapshot.equals(first.snapshot())) throw new AssertionError("Clean legacy record changed");
            second.stealInstance(data); first.stealInstance(data);
            if(first.snapshot().remaining()!=108 || second.snapshot().remaining()!=0)
                throw new AssertionError("Legacy transfer-back ownership failed");
            second.stealInstance(data);
            if(first.snapshot().remaining()!=0 || second.snapshot().remaining()!=108)
                throw new AssertionError("Legacy owner transfer failed");
            type.getMethod("delete").invoke(data);
            if(second.snapshot().remaining()!=0) throw new AssertionError("Legacy removal failed");
            first.createInstance(); first.clearForOriginShift();
            if(first.snapshot().remaining()!=0) throw new AssertionError("Legacy origin clear failed");
        }
        LegacyFlywheelMaterialSmokeTest.verify(loader);
        LegacyFlywheelCpuSmokeTest.verify(loader);
        LegacyFlywheelInstances.format(5).validateLimits(5,16,2048,2047);
        Initializer.LOGGER.info("Flywheel CPU adapter smoke passed: actual BlockModel sequential/custom indices and shading, aligned ModelData, shifted light bytes, dirty/removal/transfer/origin ownership; backend remains off");
    }
}
