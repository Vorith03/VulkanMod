package net.vulkanmod.vulkan;

import net.vulkanmod.vulkan.framebuffer.SwapChain;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.StagingBuffer;
import net.vulkanmod.vulkan.queue.GraphicsQueue;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.queue.TransferQueue;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.texture.VulkanImage;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static net.vulkanmod.vulkan.queue.Queue.getQueueFamilies;
import static net.vulkanmod.vulkan.util.VUtil.asPointerBuffer;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.glfw.GLFWVulkan.nglfwCreateWindowSurface;
import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.util.vma.Vma.vmaCreateAllocator;
import static org.lwjgl.util.vma.Vma.vmaDestroyAllocator;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class Vulkan {

    public static final boolean ENABLE_VALIDATION_LAYERS = false;
//    public static final boolean ENABLE_VALIDATION_LAYERS = true;

//    public static final boolean DYNAMIC_RENDERING = true;
    public static final boolean DYNAMIC_RENDERING = false;

    public static final Set<String> VALIDATION_LAYERS;
    static {
        if(ENABLE_VALIDATION_LAYERS) {
            VALIDATION_LAYERS = new HashSet<>();
            VALIDATION_LAYERS.add("VK_LAYER_KHRONOS_validation");
//            VALIDATION_LAYERS.add("VK_LAYER_KHRONOS_synchronization2");

        } else {
            // We are not going to use it, so we don't create it
            VALIDATION_LAYERS = null;
        }
    }

    static final Set<String> REQUIRED_EXTENSION = DYNAMIC_RENDERING ? Stream.of(
            VK_KHR_SWAPCHAIN_EXTENSION_NAME, VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME)
            .collect(toSet())
            : Stream.of(
                    VK_KHR_SWAPCHAIN_EXTENSION_NAME)
            .collect(toSet());

    private static int debugCallback(int messageSeverity, int messageType, long pCallbackData, long pUserData) {

        VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);

        String s;
        if((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
            s = "\u001B[31m" + callbackData.pMessageString();

//            System.err.println("Stack dump:");
//            Thread.dumpStack();
        } else {
            s = callbackData.pMessageString();
        }

        System.err.println(s);

        if((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0)
            System.nanoTime();

        return VK_FALSE;
    }

    private static int createDebugUtilsMessengerEXT(VkInstance instance, VkDebugUtilsMessengerCreateInfoEXT createInfo,
                                                    VkAllocationCallbacks allocationCallbacks, LongBuffer pDebugMessenger) {

        if(vkGetInstanceProcAddr(instance, "vkCreateDebugUtilsMessengerEXT") != NULL) {
            return vkCreateDebugUtilsMessengerEXT(instance, createInfo, allocationCallbacks, pDebugMessenger);
        }

        return VK_ERROR_EXTENSION_NOT_PRESENT;
    }

    private static void destroyDebugUtilsMessengerEXT(VkInstance instance, long debugMessenger, VkAllocationCallbacks allocationCallbacks) {

        if(vkGetInstanceProcAddr(instance, "vkDestroyDebugUtilsMessengerEXT") != NULL) {
            vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, allocationCallbacks);
        }

    }

    public static VkDevice getDevice() {
        return Device.device;
    }

    public static long getAllocator() {
        return allocator;
    }

    public static long window;

    private static VkInstance instance;
    private static long debugMessenger;
    private static long surface;

    private static SwapChain swapChain;

    private static long commandPool;
    private static VkCommandBuffer immediateCmdBuffer;
    private static long immediateFence;

    private static long allocator;

    private static int FramesNum;
    private static StagingBuffer[] stagingBuffers;

    public static void initVulkan(long window) {
        createInstance();
        setupDebugMessenger();
        createSurface(window);

        Device.pickPhysicalDevice(instance);
        Device.createLogicalDevice();

        createVma();
        MemoryTypes.createMemoryTypes();

        createCommandPool();
        allocateImmediateCmdBuffer();

        createSwapChain();
        MemoryManager.createInstance(swapChain.getFramesNum());

        createStagingBuffers();
        Renderer.initRenderer();
    }

    static void createStagingBuffers() {
        if(stagingBuffers != null) {
            freeStagingBuffers();
        }

        stagingBuffers = new StagingBuffer[getSwapChainImages().size()];

        for(int i = 0; i < stagingBuffers.length; ++i) {
            stagingBuffers[i] = new StagingBuffer(30 * 1024 * 1024);
        }
    }

    public static String describeStagingBuffers() {
        StagingBuffer[] buffers = stagingBuffers;
        if(buffers == null)
            return "none";

        long capacity = 0L;
        long used = 0L;
        long highWater = 0L;
        int resizeCount = 0;

        for(StagingBuffer buffer : buffers) {
            if(buffer == null)
                continue;

            capacity += buffer.getBufferSize();
            used += buffer.getUsedBytes();
            highWater += buffer.getHighWaterMark();
            resizeCount += buffer.getResizeCount();
        }

        long mib = 1024L * 1024L;
        return String.format("%d buffers cap=%dMiB used=%dMiB high=%dMiB resizes=%d",
                buffers.length, capacity / mib, used / mib, highWater / mib, resizeCount);
    }

    private static void createSwapChain() {
        swapChain = new SwapChain();

        FramesNum = swapChain.getFramesNum();
    }

    public static void recreateSwapChain() {
        int newFramesNum = swapChain.recreateSwapChain();

        if (FramesNum != newFramesNum) {
            // The device is idle when Renderer invokes this path. Retire all objects
            // owned by the old frame slots before replacing their MemoryManager.
            freeStagingBuffers();
            MemoryManager.getInstance().freeAllBuffers();
            MemoryManager.createInstance(newFramesNum);
            createStagingBuffers();
        }

        FramesNum = newFramesNum;
    }

    public static void waitIdle() {
        vkDeviceWaitIdle(Device.device);
    }

    public static void cleanUp() {
        vkDeviceWaitIdle(Device.device);
        vkDestroyCommandPool(Device.device, commandPool, null);
        vkDestroyFence(Device.device, immediateFence, null);

        Pipeline.destroyPipelineCache();
        swapChain.cleanUp();

        Renderer.getInstance().cleanUpResources();
        freeStagingBuffers();

        try {
            MemoryManager.getInstance().freeAllBuffers();
        } catch (Exception e) {
            e.printStackTrace();
        }

        vmaDestroyAllocator(allocator);

        Device.destroy();
        destroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
        KHRSurface.vkDestroySurfaceKHR(instance, surface, null);
        vkDestroyInstance(instance, null);
    }

    private static void freeStagingBuffers() {
        if(stagingBuffers == null)
            return;

        for(StagingBuffer buffer : stagingBuffers) {
            buffer.freeBuffer();
        }
        stagingBuffers = null;
    }

    private static void createInstance() {

        if(ENABLE_VALIDATION_LAYERS && !checkValidationLayerSupport()) {
            throw new RuntimeException("Validation requested but not supported");
        }

        try(MemoryStack stack = stackPush()) {

            // Use calloc to initialize the structs with 0s. Otherwise, the program can crash due to random values

            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack);

            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
            appInfo.pApplicationName(stack.UTF8Safe("VulkanMod"));
            appInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.pEngineName(stack.UTF8Safe("No Engine"));
            appInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.apiVersion(VK_API_VERSION_1_2);

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack);

            createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            createInfo.pApplicationInfo(appInfo);
            // enabledExtensionCount is implicitly set when you call ppEnabledExtensionNames
            createInfo.ppEnabledExtensionNames(getRequiredExtensions());

            if(ENABLE_VALIDATION_LAYERS) {

                createInfo.ppEnabledLayerNames(asPointerBuffer(VALIDATION_LAYERS));
            }

            // If a debug callback will be used in pNext chain, initialize it explicitly
            try(MemoryStack stackDebug = stackPush()) {
                VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = null;
                if (ENABLE_VALIDATION_LAYERS) {
                    debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stackDebug);
                    populateDebugMessengerCreateInfo(debugCreateInfo);
                    createInfo.pNext(debugCreateInfo.address());
                }

                LongBuffer pInstance = stack.mallocLong(1);

                int result = vkCreateInstance(createInfo, null, pInstance);
                if(result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create Vulkan instance: " + result);
                }

                instance = new VkInstance(pInstance.get(0), createInfo);
            }
        }
    }

    private static void populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo) {
        debugCreateInfo.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);
        debugCreateInfo.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT |
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
        debugCreateInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);
        debugCreateInfo.pfnUserCallback(Vulkan::debugCallback);
    }

    private static void setupDebugMessenger() {
        if(!ENABLE_VALIDATION_LAYERS)
            return;

        try(MemoryStack stack = stackPush()) {
            VkDebugUtilsMessengerCreateInfoEXT createInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack);
            populateDebugMessengerCreateInfo(createInfo);

            LongBuffer pDebugMessenger = stack.mallocLong(1);

            if(createDebugUtilsMessengerEXT(instance, createInfo, null, pDebugMessenger) != VK_SUCCESS) {
                throw new RuntimeException("Failed to set up debug messenger");
            }

            debugMessenger = pDebugMessenger.get(0);
        }
    }

    private static void createSurface(long window) {
        try(MemoryStack stack = stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);

            int result = nglfwCreateWindowSurface(instance.address(), window, NULL, memAddress(pSurface));
            if(result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create window surface: " + result);
            }

            surface = pSurface.get(0);
        }
    }

    private static void createVma() {
        try(MemoryStack stack = stackPush()) {
            VmaVulkanFunctions functions = VmaVulkanFunctions.calloc(stack);
            functions.set(instance, Device.device);

            VmaAllocatorCreateInfo allocatorInfo = VmaAllocatorCreateInfo.calloc(stack);
            allocatorInfo.physicalDevice(Device.physicalDevice);
            allocatorInfo.device(Device.device);
            allocatorInfo.instance(instance);
            allocatorInfo.pVulkanFunctions(functions);
            allocatorInfo.vulkanApiVersion(VK_API_VERSION_1_2);

            LongBuffer pAllocator = stack.mallocLong(1);

            int result = vmaCreateAllocator(allocatorInfo, pAllocator);
            if(result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VMA allocator: " + result);
            }

            allocator = pAllocator.get(0);
        }
    }

    public static SwapChain getSwapChain() {
        return swapChain;
    }

    public static StagingBuffer getStagingBuffer(int frame) {
        return stagingBuffers[frame];
    }

    public static List<VulkanImage> getSwapChainImages() {
        return swapChain.getSwapChainImages();
    }

    public static int getFramesNum() { return FramesNum; }

    public static VkInstance getInstance() { return instance; }

    public static long getSurface() { return surface; }

    public static long getCommandPool() { return commandPool; }

    public static VkCommandBuffer getImmediateCmdBuffer() { return immediateCmdBuffer; }

    public static long getImmediateFence() { return immediateFence; }

    public static void recreateSwapchainIfNeeded() {
        if(swapChain != null && swapChain.shouldRecreate()) {
            recreateSwapChain();
        }
    }

    public static void immediateSubmit(java.util.function.Consumer<VkCommandBuffer> consumer) {
        synchronized (Vulkan.class) {
            vkResetFences(Device.device, immediateFence);
            vkResetCommandBuffer(immediateCmdBuffer, 0);

            try(MemoryStack stack = stackPush()) {
                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
                beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
                beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

                if(vkBeginCommandBuffer(immediateCmdBuffer, beginInfo) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to begin immediate command buffer");
                }

                consumer.accept(immediateCmdBuffer);

                if(vkEndCommandBuffer(immediateCmdBuffer) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to end immediate command buffer");
                }

                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
                submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
                submitInfo.pCommandBuffers(stack.pointers(immediateCmdBuffer));

                if(vkQueueSubmit(Device.getGraphicsQueue().getQueue(), submitInfo, immediateFence) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to submit immediate command buffer");
                }

                vkWaitForFences(Device.device, immediateFence, true, VUtil.UINT64_MAX);
            }
        }
    }

    private static void createCommandPool() {
        try(MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.queueFamilyIndex(getQueueFamilies().graphicsFamily);
            poolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);
            if(vkCreateCommandPool(Device.device, poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }

            commandPool = pCommandPool.get(0);
        }
    }

    private static void allocateImmediateCmdBuffer() {
        try(MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(commandPool);
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocInfo.commandBufferCount(1);

            PointerBuffer pCommandBuffer = stack.mallocPointer(1);
            if(vkAllocateCommandBuffers(Device.device, allocInfo, pCommandBuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate immediate command buffer");
            }

            immediateCmdBuffer = new VkCommandBuffer(pCommandBuffer.get(0), Device.device);
        }

        try(MemoryStack stack = stackPush()) {
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);

            LongBuffer pFence = stack.mallocLong(1);
            if(vkCreateFence(Device.device, fenceInfo, null, pFence) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create immediate fence");
            }

            immediateFence = pFence.get(0);
        }
    }

    public static DeviceInfo getDeviceInfo() {
        return Device.getDeviceInfo();
    }
}
