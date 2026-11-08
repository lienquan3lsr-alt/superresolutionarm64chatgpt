/*
 * Super Resolution
 * Copyright (c) 2026. 187J3X1-114514
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

package io.homo.superresolution.common.gui.config.pages;

import io.homo.superresolution.common.gui.impl.Text;
import io.homo.superresolution.api.platform.OperatingSystemType;
import io.homo.superresolution.core.graphics.GraphicsCapabilities;
import io.homo.superresolution.core.RenderSystems;
import io.homo.superresolution.core.gui.core.ContainerWidget;
import io.homo.superresolution.core.gui.core.frame.Frame;
import io.homo.superresolution.core.gui.core.frame.ScrollableFrame;
import io.homo.superresolution.thirdparty.yoga.appliedenergistics.yoga.*;

import io.homo.superresolution.common.gui.config.pages.ConfigPageContext.TitlePill;
import io.homo.superresolution.common.gui.config.pages.ConfigPageContext.InfoCard;

public final class EnvironmentInfoPage implements ConfigPage {
    public static final EnvironmentInfoPage INSTANCE = new EnvironmentInfoPage();

    private EnvironmentInfoPage() {
    }

    @Override
    public Frame create(ConfigPageContext context) {
        return build(context);
    }

    private static Frame build(ConfigPageContext context) {
        ScrollableFrame frame = context.createStandardScrollableFrame();
        ContainerWidget container = context.createStandardContainer();
        context.addFrameTitle(container, Text.translatable("superresolution.screen.config.section.environment"));

        TitlePill label = context.createSectionPill(
                Text.translatable("superresolution.screen.config.info.environment.base").getString()
        );
        label.layout().setMargin(YogaEdge.TOP, 8);
        label.layout().setMargin(YogaEdge.BOTTOM, 6);
        container.addChild(label);

        InfoCard envCard = new InfoCard();
        envCard.addChild(context.createInfoLine(Text.translatable("superresolution.screen.config.info.environment.mod_version").getString(), context.safeGetModVersion()));
        envCard.addChild(context.createInfoLine(Text.translatable("superresolution.screen.config.info.environment.native_version").getString(), context.safeGetNativeVersion()));
        envCard.addChild(context.createInfoLine(Text.translatable("superresolution.screen.config.info.environment.system").getString(), context.safeGetOperatingSystem()));
        container.addChild(envCard);
        TitlePill labelOGL = context.createSectionPill(
                Text.translatable("superresolution.screen.config.info.environment.opengl").getString()
        );
        labelOGL.layout().setMargin(YogaEdge.TOP, 8);
        labelOGL.layout().setMargin(YogaEdge.BOTTOM, 6);
        container.addChild(labelOGL);

        container.addChild(context.createGraphicsInfoCard(
                Text.translatable("superresolution.screen.config.info.environment.opengl").getString(),
                GraphicsCapabilities.getGLVersionString(),
                GraphicsCapabilities.getGLExtensions()
        ));
        TitlePill labelVK = context.createSectionPill(
                Text.translatable("superresolution.screen.config.info.environment.vulkan").getString()
        );
        labelVK.layout().setMargin(YogaEdge.TOP, 8);
        labelVK.layout().setMargin(YogaEdge.BOTTOM, 6);
        container.addChild(labelVK);

        container.addChild(context.createGraphicsInfoCard(
                Text.translatable("superresolution.screen.config.info.environment.vulkan").getString(),
                GraphicsCapabilities.getVulkanVersionString(),
                GraphicsCapabilities.getVulkanDeviceExtensions()
        ));

        // Android-only, read-only preflight. An extension list is not proof that
        // the driver can successfully export/import a resource at runtime.
        if (OperatingSystemType.get() == OperatingSystemType.ANDROID) {
            TitlePill labelInterop = context.createSectionPill("Android OpenGL–Vulkan interop (experimental)");
            labelInterop.layout().setMargin(YogaEdge.TOP, 8);
            labelInterop.layout().setMargin(YogaEdge.BOTTOM, 6);
            container.addChild(labelInterop);

            java.util.Set<String> glExtensions = GraphicsCapabilities.getGLExtensions();
            boolean vulkanRhiAvailable = RenderSystems.isSupportVulkan();
            java.util.Set<String> vkExtensions = vulkanRhiAvailable
                    ? GraphicsCapabilities.getVulkanDeviceExtensions()
                    : java.util.Set.of();
            java.util.List<String> requiredGl = java.util.List.of(
                    "GL_EXT_memory_object",
                    "GL_EXT_memory_object_fd",
                    "GL_EXT_semaphore",
                    "GL_EXT_semaphore_fd"
            );
            java.util.List<String> requiredVk = java.util.List.of(
                    "VK_KHR_external_memory_fd",
                    "VK_KHR_external_semaphore_fd"
            );
            java.util.List<String> missingGl = requiredGl.stream().filter(e -> !glExtensions.contains(e)).toList();
            java.util.List<String> missingVk = requiredVk.stream().filter(e -> !vkExtensions.contains(e)).toList();

            InfoCard interopCard = new InfoCard();
            interopCard.addChild(context.createInfoLine(
                    "OpenGL external memory/semaphore",
                    missingGl.isEmpty() ? "All listed extensions exposed" : "Missing: " + String.join(", ", missingGl)
            ));
            interopCard.addChild(context.createInfoLine(
                    "Vulkan RHI",
                    vulkanRhiAvailable
                            ? "Initialized; extensions below are from the selected physical device"
                            : "Unavailable; Vulkan FD support is unknown (not confirmed missing)"
            ));
            interopCard.addChild(context.createInfoLine(
                    "Vulkan external FD",
                    !vulkanRhiAvailable
                            ? "Not queried: no Super Resolution Vulkan device"
                            : (missingVk.isEmpty() ? "All listed extensions exposed" : "Missing: " + String.join(", ", missingVk))
            ));
            interopCard.addChild(context.createInfoLine(
                    "OPAQUE_FD image capability",
                    vulkanRhiAvailable
                            ? io.homo.superresolution.core.graphics.vulkan.AndroidExternalInteropProbe.imageStatus()
                            : "Not queried: Vulkan RHI unavailable"
            ));
            interopCard.addChild(context.createInfoLine(
                    "OPAQUE_FD semaphore capability",
                    vulkanRhiAvailable
                            ? io.homo.superresolution.core.graphics.vulkan.AndroidExternalInteropProbe.semaphoreStatus()
                            : "Not queried: Vulkan RHI unavailable"
            ));
            String status;
            if (!vulkanRhiAvailable) {
                status = "Incomplete: Vulkan RHI did not initialize; check latest.log for loader/device/UUID errors";
            } else if (!missingGl.isEmpty() || !missingVk.isEmpty()) {
                status = "Not ready: one or more required external-memory/semaphore extensions are missing";
            } else {
                status = "Candidate path only: extensions exposed; image import and semaphore round-trip are NOT verified";
            }
            interopCard.addChild(context.createInfoLine("Preflight result", status));
            container.addChild(interopCard);
        }

        context.finalizeFrame(frame, container);
        return frame;
    }

}
