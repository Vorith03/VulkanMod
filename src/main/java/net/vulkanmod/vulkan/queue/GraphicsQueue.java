package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.Synchronization;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public class GraphicsQueue extends Queue {
    public static GraphicsQueue INSTANCE;

    private static CommandPool.CommandBuffer currentCmdBuffer;

    public GraphicsQueue(MemoryStack stack, int familyIndex) {
        super(stack, familyIndex);
    }

    @Override
    public synchronized long submitCommands(CommandPool.CommandBuffer commandBuffer) {
        // A command buffer owned by the current texture-upload batch must only be
        // submitted by endRecordingAndSubmit(). Leaf helpers may append commands,
        // but they must not terminate the shared batch out from under its owner.
        if(commandBuffer == currentCmdBuffer)
            return VK_NULL_HANDLE;

        long fence = super.submitCommands(commandBuffer, true);
        Synchronization.INSTANCE.addCommandBuffer(commandBuffer, true);
        return fence;
    }

    public synchronized void startRecording() {
        if(currentCmdBuffer != null)
            throw new IllegalStateException("Graphics upload batch already recording");

        currentCmdBuffer = beginCommands();
    }

    public synchronized void endRecordingAndSubmit() {
        CommandPool.CommandBuffer commandBuffer = currentCmdBuffer;
        if(commandBuffer == null)
            return;

        // Release ownership before submission so submitCommands() can distinguish
        // the batch owner from an accidental leaf submission.
        currentCmdBuffer = null;
        submitCommands(commandBuffer);
    }

    public synchronized CommandPool.CommandBuffer getCommandBuffer() {
        if (currentCmdBuffer != null) {
            return currentCmdBuffer;
        } else {
            return beginCommands();
        }
    }

    public synchronized boolean isRecording(CommandPool.CommandBuffer commandBuffer) {
        return commandBuffer != null && commandBuffer == currentCmdBuffer;
    }

    public synchronized long endIfNeeded(CommandPool.CommandBuffer commandBuffer) {
        if (commandBuffer == currentCmdBuffer) {
            return VK_NULL_HANDLE;
        } else {
            return submitCommands(commandBuffer);
        }
    }

}
