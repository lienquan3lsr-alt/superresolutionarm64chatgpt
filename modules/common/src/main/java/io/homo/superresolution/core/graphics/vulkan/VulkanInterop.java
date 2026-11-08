/*
 * Super Resolution
 * Copyright (c) 2025-2026. 187J3X1-114514
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.homo.superresolution.core.graphics.vulkan;

import com.sun.jna.Pointer;
import io.homo.superresolution.api.platform.OperatingSystemType;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.EXTMemoryObjectFD;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.EXTSemaphoreFD;
import org.lwjgl.opengl.EXTSemaphoreWin32;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Struct;
import org.lwjgl.system.linux.UNISTD;
import org.lwjgl.vulkan.*;

import java.lang.reflect.Method;

import static io.homo.superresolution.core.graphics.vulkan.VulkanUtils.VK_CHECK;
import static org.lwjgl.vulkan.VK11.*;

public class VulkanInterop {
    public static final VulkanInteropExt IMPL;

    static {
        OperatingSystemType os = OperatingSystemType.get();
        if (os == OperatingSystemType.WINDOWS) {
            IMPL = new WindowsVulkanInteropExtImpl();
        } else if (os == OperatingSystemType.LINUX || os == OperatingSystemType.ANDROID) {
            // Android's Vulkan OPAQUE_FD and OpenGL *_fd imports use POSIX file descriptors.
            IMPL = new LinuxVulkanInteropExtImpl();
        } else {
            throw new UnsupportedOperationException(
                    "External Vulkan/OpenGL interop is not implemented for " + os
            );
        }
    }

    public static void closeUnimportedExportedHandle(long handle) {
        if (handle == -1L) {
            return;
        }
        switch (OperatingSystemType.get()) {
            case WINDOWS -> closeWin32Handle(handle);
            case LINUX -> {
                // Linux OPAQUE_FD is a POSIX file descriptor. Close it only when GL did not take ownership.
                if (handle >= 0L && closeLinuxFileDescriptor(Math.toIntExact(handle)) != 0) {
                    throw new IllegalStateException("Failed to close exported Linux file descriptor");
                }
            }
            case ANDROID -> {
                // Use the Android framework wrapper instead of LWJGL's Linux UNISTD binding.
                // This avoids resolving an unavailable Linux native symbol during Android cleanup.
                if (handle >= 0L) closeAndroidFileDescriptor(Math.toIntExact(handle));
            }
            default -> throw new UnsupportedOperationException("Unsupported external-handle platform");
        }
    }

    public static void closeImportedExportedHandle(long handle) {
        if (OperatingSystemType.get() == OperatingSystemType.WINDOWS) {
            closeWin32Handle(handle);
        }
    }

    private static void closeWin32Handle(long handle) {
        if (handle == -1L) {
            return;
        }
        if (!WinKernel32.INSTANCE.CloseHandle(Pointer.createConstant(handle))) {
            throw new IllegalStateException("Failed to close exported Win32 external handle");
        }
    }

    private static Method findLinuxClose() {
        try {
            return UNISTD.class.getMethod("nclose", long.class, int.class);
        } catch (NoSuchMethodException ignored) {
            try {
                return UNISTD.class.getMethod("close", int.class);
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException("LWJGL does not expose a POSIX close function", exception);
            }
        }
    }

    private static int closeLinuxFileDescriptor(int fileDescriptor) {
        try {
            Method closeMethod = findLinuxClose();
            Object result = closeMethod.getParameterCount() == 2
                    ? closeMethod.invoke(null, 0L, fileDescriptor)
                    : closeMethod.invoke(null, fileDescriptor);
            return (int) result;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to close exported Linux file descriptor", exception);
        }
    }

    private static void closeAndroidFileDescriptor(int fileDescriptor) {
        Object parcelFileDescriptor = null;
        try {
            Class<?> pfdClass = Class.forName("android.os.ParcelFileDescriptor");
            parcelFileDescriptor = pfdClass.getMethod("adoptFd", int.class).invoke(null, fileDescriptor);
            pfdClass.getMethod("close").invoke(parcelFileDescriptor);
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof java.lang.reflect.InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : exception;
            throw new IllegalStateException("Failed to close exported Android POSIX file descriptor", cause);
        }
    }

    public interface VulkanInteropExt {
        void glImportSemaphoreHandleEXT(
                MemoryStack stack,
                int semaphore,
                long handle
        );

        long vkGetSemaphoreHandleKHR(
                MemoryStack stack,
                VkDevice device,
                Struct pGetHandleInfo
        );


        Struct createVkSemaphoreGetHandleInfoKHR(MemoryStack stack, long pVkSemaphore);

        Struct createVkExportSemaphoreCreateInfo(MemoryStack stack);

        Struct createVkExportMemoryAllocateInfo(MemoryStack stack);

        Struct createVkImportMemoryInfo(MemoryStack stack, long handle);

        long vkGetMemoryHandle(MemoryStack stack, VkDevice device, long memory);

        void glImportMemoryEXT(int memoryObject, long size, long handle);
    }

    private static class LinuxVulkanInteropExtImpl implements VulkanInteropExt {

        @Override
        public void glImportSemaphoreHandleEXT(MemoryStack stack, int semaphore, long handle) {
            EXTSemaphoreFD.glImportSemaphoreFdEXT(semaphore, EXTSemaphoreFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT, (int) handle);
        }

        @Override
        public long vkGetSemaphoreHandleKHR(MemoryStack stack, VkDevice device, Struct pGetHandleInfo) {
            int[] fd = new int[]{0};
            VK_CHECK(KHRExternalSemaphoreFd.vkGetSemaphoreFdKHR(device, (VkSemaphoreGetFdInfoKHR) pGetHandleInfo, fd), "Failed to export Linux semaphore handle");
            return fd[0];
        }

        @Override
        public Struct createVkSemaphoreGetHandleInfoKHR(MemoryStack stack, long pVkSemaphore) {
            VkSemaphoreGetFdInfoKHR semaphoreGetInfo = VkSemaphoreGetFdInfoKHR.calloc(stack);
            semaphoreGetInfo.sType(KHRExternalSemaphoreFd.VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR);
            semaphoreGetInfo.handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
            semaphoreGetInfo.semaphore(pVkSemaphore);
            return semaphoreGetInfo;
        }

        @Override
        public Struct createVkExportSemaphoreCreateInfo(MemoryStack stack) {
            VkExportSemaphoreCreateInfo exportSemaphoreCreateInfo = VkExportSemaphoreCreateInfo.calloc(stack);
            exportSemaphoreCreateInfo.sType(VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO);
            exportSemaphoreCreateInfo.handleTypes(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
            return exportSemaphoreCreateInfo;
        }

        @Override
        public Struct createVkExportMemoryAllocateInfo(MemoryStack stack) {
            return VkExportMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO)
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);
        }

        @Override
        public Struct createVkImportMemoryInfo(MemoryStack stack, long handle) {
            return VkImportMemoryFdInfoKHR.calloc(stack)
                    .sType(KHRExternalMemoryFd.VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR)
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT)
                    .fd((int) handle);
        }

        @Override
        public long vkGetMemoryHandle(MemoryStack stack, VkDevice device, long memory) {
            VkMemoryGetFdInfoKHR getInfo = VkMemoryGetFdInfoKHR.calloc(stack)
                    .sType(KHRExternalMemoryFd.VK_STRUCTURE_TYPE_MEMORY_GET_FD_INFO_KHR)
                    .memory(memory)
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);

            int[] fd = new int[1];
            VK_CHECK(KHRExternalMemoryFd.vkGetMemoryFdKHR(device, getInfo, fd),
                    "Failed to export Linux memory handle");
            return fd[0];
        }

        @Override
        public void glImportMemoryEXT(int memoryObject, long size, long handle) {
            EXTMemoryObjectFD.glImportMemoryFdEXT(
                    memoryObject,
                    size,
                    EXTMemoryObjectFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT,
                    (int) handle
            );
        }
    }

    private static class WindowsVulkanInteropExtImpl implements VulkanInteropExt {
        @Override
        public void glImportSemaphoreHandleEXT(MemoryStack stack, int semaphore, long handle) {
            EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(semaphore, EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);
        }

        @Override
        public long vkGetSemaphoreHandleKHR(MemoryStack stack, VkDevice device, Struct pGetHandleInfo) {
            PointerBuffer fd = stack.callocPointer(1);
            VK_CHECK(KHRExternalSemaphoreWin32.vkGetSemaphoreWin32HandleKHR(
                    device,
                    (VkSemaphoreGetWin32HandleInfoKHR) pGetHandleInfo,
                    fd
            ), "Failed to export Windows memory handle");
            return fd.get(0);
        }

        @Override
        public Struct createVkSemaphoreGetHandleInfoKHR(MemoryStack stack, long pVkSemaphore) {
            VkSemaphoreGetWin32HandleInfoKHR semaphoreGetInfo = VkSemaphoreGetWin32HandleInfoKHR.calloc(stack);
            semaphoreGetInfo.sType(KHRExternalSemaphoreWin32.VK_STRUCTURE_TYPE_SEMAPHORE_GET_WIN32_HANDLE_INFO_KHR);
            semaphoreGetInfo.handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT);
            semaphoreGetInfo.semaphore(pVkSemaphore);
            return semaphoreGetInfo;
        }

        @Override
        public Struct createVkExportSemaphoreCreateInfo(MemoryStack stack) {
            VkExportSemaphoreCreateInfo exportSemaphoreCreateInfo = VkExportSemaphoreCreateInfo.calloc(stack);
            exportSemaphoreCreateInfo.sType(VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO);
            exportSemaphoreCreateInfo.handleTypes(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT);
            return exportSemaphoreCreateInfo;
        }

        @Override
        public Struct createVkExportMemoryAllocateInfo(MemoryStack stack) {
            return VkExportMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO)
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
        }

        @Override
        public Struct createVkImportMemoryInfo(MemoryStack stack, long handle) {
            return VkImportMemoryWin32HandleInfoKHR.calloc(stack)
                    .sType(KHRExternalMemoryWin32.VK_STRUCTURE_TYPE_IMPORT_MEMORY_WIN32_HANDLE_INFO_KHR)
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT)
                    .handle(handle);
        }

        @Override
        public long vkGetMemoryHandle(MemoryStack stack, VkDevice device, long memory) {
            VkMemoryGetWin32HandleInfoKHR getInfo = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                    .sType(KHRExternalMemoryWin32.VK_STRUCTURE_TYPE_MEMORY_GET_WIN32_HANDLE_INFO_KHR)
                    .memory(memory)
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);

            PointerBuffer pHandle = stack.mallocPointer(1);
            VK_CHECK(KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR(device, getInfo, pHandle),
                    "Failed to export Windows memory handle");
            return pHandle.get(0);
        }

        @Override
        public void glImportMemoryEXT(int memoryObject, long size, long handle) {
            EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(
                    memoryObject,
                    size,
                    EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT,
                    handle
            );
        }
    }
}
