/*
 * Super Resolution
 * Copyright (c) 2026. 187J3X1-114514
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.homo.superresolution.core.graphics.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRExternalMemoryCapabilities.*;
import static org.lwjgl.vulkan.KHRExternalMemoryFd.VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreCapabilities.*;
import static org.lwjgl.vulkan.KHRExternalSemaphoreFd.VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

/**
 * Read-only Android OPAQUE_FD capability probe. It queries a representative
 * RGBA8 image configuration and binary semaphore support. It never creates,
 * exports, or imports external handles, so it is safe during RHI startup.
 * A positive result is necessary but not sufficient for cross-API interop.
 */
public final class AndroidExternalInteropProbe {
    private static volatile String imageStatus = "Not queried";
    private static volatile String semaphoreStatus = "Not queried";

    private AndroidExternalInteropProbe() {}

    public static String imageStatus() { return imageStatus; }
    public static String semaphoreStatus() { return semaphoreStatus; }

    public static void inspect(VkPhysicalDevice physicalDevice, List<String> deviceExtensions) {
        imageStatus = deviceExtensions.contains(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME)
                ? inspectImage(physicalDevice)
                : "Unsupported: VK_KHR_external_memory_fd not exposed";
        semaphoreStatus = deviceExtensions.contains(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME)
                ? inspectSemaphore(physicalDevice)
                : "Unsupported: VK_KHR_external_semaphore_fd not exposed";

        VkRenderSystem.LOGGER.info("Android OPAQUE_FD probe — image: {}", imageStatus);
        VkRenderSystem.LOGGER.info("Android OPAQUE_FD probe — semaphore: {}", semaphoreStatus);
    }

    private static String inspectImage(VkPhysicalDevice physicalDevice) {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceExternalImageFormatInfo externalInfo =
                    VkPhysicalDeviceExternalImageFormatInfo.calloc(stack)
                            .sType$Default()
                            .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT_KHR);
            VkPhysicalDeviceImageFormatInfo2 formatInfo =
                    VkPhysicalDeviceImageFormatInfo2.calloc(stack)
                            .sType$Default()
                            .pNext(externalInfo.address())
                            .format(VK_FORMAT_R8G8B8A8_UNORM)
                            .type(VK_IMAGE_TYPE_2D)
                            .tiling(VK_IMAGE_TILING_OPTIMAL)
                            .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                            .flags(0);
            VkExternalImageFormatProperties externalProperties =
                    VkExternalImageFormatProperties.calloc(stack).sType$Default();
            VkImageFormatProperties2 formatProperties =
                    VkImageFormatProperties2.calloc(stack)
                            .sType$Default()
                            .pNext(externalProperties.address());

            int result = vkGetPhysicalDeviceImageFormatProperties2(
                    physicalDevice, formatInfo, formatProperties);
            if (result != VK_SUCCESS) {
                return "RGBA8 optimal sampled+transfer-src rejected (VkResult " + result + ")";
            }
            int features = externalProperties.externalMemoryProperties().externalMemoryFeatures();
            boolean exportable = (features & VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT_KHR) != 0;
            boolean importable = (features & VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT_KHR) != 0;
            boolean dedicatedOnly = (features & VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT_KHR) != 0;
            return "RGBA8 optimal sampled+transfer-src: exportable=" + exportable
                    + ", importable=" + importable + ", dedicatedOnly=" + dedicatedOnly;
        } catch (Throwable failure) {
            return "Probe failed safely: " + failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
    }

    private static String inspectSemaphore(VkPhysicalDevice physicalDevice) {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceExternalSemaphoreInfo info =
                    VkPhysicalDeviceExternalSemaphoreInfo.calloc(stack)
                            .sType$Default()
                            .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT_KHR);
            VkExternalSemaphoreProperties properties =
                    VkExternalSemaphoreProperties.calloc(stack).sType$Default();
            vkGetPhysicalDeviceExternalSemaphoreProperties(physicalDevice, info, properties);
            int features = properties.externalSemaphoreFeatures();
            boolean exportable = (features & VK_EXTERNAL_SEMAPHORE_FEATURE_EXPORTABLE_BIT_KHR) != 0;
            boolean importable = (features & VK_EXTERNAL_SEMAPHORE_FEATURE_IMPORTABLE_BIT_KHR) != 0;
            return "OPAQUE_FD binary semaphore: exportable=" + exportable + ", importable=" + importable;
        } catch (Throwable failure) {
            return "Probe failed safely: " + failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
    }
}
