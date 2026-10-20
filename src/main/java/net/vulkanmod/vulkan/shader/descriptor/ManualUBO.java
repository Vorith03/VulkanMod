package net.vulkanmod.vulkan.shader.descriptor;

import org.lwjgl.system.MemoryUtil;

public class ManualUBO extends UBO {

    private long srcPtr;
    private int srcByteCount;

    public ManualUBO(int binding, int type, int wordCount) {
        super(binding, type, wordCount * 4, null);
    }

    @Override
    public void update(long ptr) {
        //update manually
        MemoryUtil.memCopy(this.srcPtr, ptr, this.srcByteCount);
    }

    public void setSrc(long ptr, int byteCount) {
        this.srcPtr = ptr;
        this.srcByteCount = byteCount;
    }
}
