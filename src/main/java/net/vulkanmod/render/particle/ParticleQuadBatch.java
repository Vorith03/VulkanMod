package net.vulkanmod.render.particle;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Offsets belong to one immutable RenderedBuffer, never to a global particle pass. */
public final class ParticleQuadBatch {
    public static final int STRIDE = 28, QUAD_BYTES = STRIDE * 4, MAX_QUADS = 65536;
    private int[] offsets = new int[32];
    private int count;
    public int count() { return count; }
    public int offset(int i) { return offsets[i]; }
    public void add(int offset) {
        if(count >= MAX_QUADS || offset < 0 || offset % QUAD_BYTES != 0
                || count > 0 && offset <= offsets[count - 1])
            throw new IllegalArgumentException("Invalid billboard offset/order/capacity");
        if(count == offsets.length) offsets = Arrays.copyOf(offsets, Math.min(MAX_QUADS, count * 2));
        offsets[count++] = offset;
    }
    public void validate(int bytes) {
        if(count == 0 || bytes < QUAD_BYTES || bytes % QUAD_BYTES != 0
                || offsets[count - 1] > bytes - QUAD_BYTES)
            throw new IllegalArgumentException("Billboards exceed vertex slice");
    }
    public static void pack(ByteBuffer b, int at, float x, float y, float z, Quaternionf q, float size,
                            float u0, float u1, float v0, float v1, int color, int light) {
        b.putFloat(at,x).putFloat(at+4,y).putFloat(at+8,z).putFloat(at+12,size);
        b.putFloat(at+16,q.x).putFloat(at+20,q.y).putFloat(at+24,q.z).putFloat(at+28,q.w);
        b.putFloat(at+32,u0).putFloat(at+36,u1).putFloat(at+40,v0).putFloat(at+44,v1);
        b.putInt(at+48,color).putInt(at+52,light);
    }
    /** Exceptional fallback only, before upload; also materializes before a CPU sorting request. */
    public void expandCpu(ByteBuffer b) {
        validate(b.remaining());
        int base = b.position();
        Quaternionf q = new Quaternionf();
        Vector3f v = new Vector3f();
        for(int i=0;i<count;i++) {
            int at=base+offsets[i];
            float x=b.getFloat(at), y=b.getFloat(at+4), z=b.getFloat(at+8), size=b.getFloat(at+12);
            q.set(b.getFloat(at+16),b.getFloat(at+20),b.getFloat(at+24),b.getFloat(at+28));
            float u0=b.getFloat(at+32), u1=b.getFloat(at+36), v0=b.getFloat(at+40), v1=b.getFloat(at+44);
            int color=b.getInt(at+48), light=b.getInt(at+52);
            for(int j=0;j<4;j++) {
                v.set(j<2?-1:1, j==0||j==3?-1:1, 0).rotate(q).mul(size).add(x,y,z);
                int dst=at+j*STRIDE;
                b.putFloat(dst,v.x).putFloat(dst+4,v.y).putFloat(dst+8,v.z);
                b.putFloat(dst+12,j<2?u1:u0).putFloat(dst+16,j==0||j==3?v1:v0);
                b.putInt(dst+20,color).putInt(dst+24,light);
            }
        }
    }
}
