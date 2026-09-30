package net.vulkanmod.vulkan.util;

import com.sun.management.ThreadMXBean;
import org.joml.Matrix4f;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public final class MatrixProductTest {
    public static void main(String[] args) {
        MatrixProduct product = new MatrixProduct();
        ByteBuffer projection = bytes();
        ByteBuffer modelView = bytes();
        ByteBuffer output = bytes();
        ByteBuffer expected = bytes();
        Random random = new Random(20260930L);
        for(int sample = 0; sample < 1000; ++sample) {
            // Exercise nonzero positions as well as arbitrary, perspective and
            // affine matrices. JOML's optimized multiplication properties must
            // be reset when scratch matrices are overwritten each call.
            projection.position(8);
            modelView.position(12);
            output.position(4);
            expected.position(4);
            Matrix4f p = randomMatrix(random);
            Matrix4f m = randomMatrix(random);
            if(sample % 3 == 0) p.identity().perspective(1.2f, 1.8f, 0.1f, 1024f);
            if(sample % 3 == 1) m.identity().translate(4, -3, 8).rotateY(0.7f);
            p.get(projection);
            m.get(modelView);
            legacy(projection, modelView, expected);
            product.write(projection, modelView, output);
            for(int offset = 0; offset < 64; offset += 4) {
                if(output.getInt(4 + offset) != expected.getInt(4 + offset))
                    throw new AssertionError("MVP mismatch sample=" + sample + " offset=" + offset);
            }
            if(projection.position() != 8 || modelView.position() != 12 || output.position() != 4)
                throw new AssertionError("MVP changed buffer position");
            // A caller may choose an input buffer as its output.
            product.write(projection, modelView, projection);
            for(int offset = 0; offset < 64; offset += 4)
                if(projection.getInt(8 + offset) != expected.getInt(4 + offset))
                    throw new AssertionError("Aliased MVP output mismatch");
        }
        ThreadMXBean bean = (ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(bean.isThreadAllocatedMemorySupported()) {
            bean.setThreadAllocatedMemoryEnabled(true);
            for(int i = 0; i < 100_000; ++i) product.write(projection, modelView, output);
            long thread = Thread.currentThread().getId();
            long before = bean.getThreadAllocatedBytes(thread);
            for(int i = 0; i < 100_000; ++i) product.write(projection, modelView, output);
            long allocation = bean.getThreadAllocatedBytes(thread) - before;
            if(allocation > 4096) throw new AssertionError("Steady MVP allocated " + allocation + " bytes");
            System.out.println("100000 warmed MVP updates allocated " + allocation + " bytes");
            for(int i = 0; i < 100_000; ++i) legacy(projection, modelView, output);
            before = bean.getThreadAllocatedBytes(thread);
            for(int i = 0; i < 100_000; ++i) legacy(projection, modelView, output);
            System.out.println("100000 warmed legacy MVP updates allocated "
                    + (bean.getThreadAllocatedBytes(thread) - before) + " bytes");
        }
        System.out.println("MVP values, input positions, scratch reuse and output aliasing passed");
    }

    private static ByteBuffer bytes() {
        return ByteBuffer.allocateDirect(80).order(ByteOrder.nativeOrder());
    }

    private static Matrix4f randomMatrix(Random random) {
        float[] values = new float[16];
        for(int i = 0; i < 16; ++i) values[i] = random.nextFloat() * 4 - 2;
        return new Matrix4f().set(values);
    }

    private static void legacy(ByteBuffer p, ByteBuffer m, ByteBuffer out) {
        new Matrix4f(p.asFloatBuffer()).mul(new Matrix4f(m.asFloatBuffer())).get(out);
    }
}
