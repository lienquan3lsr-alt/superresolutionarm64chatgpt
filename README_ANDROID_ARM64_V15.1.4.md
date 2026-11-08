# Super Resolution Android ARM64 — Patch V15.1.4

**This ZIP is a source overlay/installer, not a prebuilt JAR.** It is designed for the existing full repository `lienquan3lsr-alt/superresolutionarm64chatgpt` on branch `dev`.

## What V15.1.4 adds

1. `apply-v15-overlay.sh` now supports a separate overlay directory and a target repository path. It installs `common/`, `native/`, the Minecraft config, preflight script, and GitHub Actions workflow into the correct locations.
2. Before changing the target, it verifies that the full source repository and Gradle wrapper already exist. It does not fabricate placeholder Java/C++ source or a fake wrapper.
3. The original `configs/1.21.11.json` is saved as `configs/1.21.11.json.pre-v15` before replacement when applying from a separate overlay directory.
4. The target config is explicitly checked for Minecraft 1.21.11, Java 25, Fabric Loader 0.20.1, and Sodium 0.8.15 beta.
5. The Android JAR workflow can run manually (`workflow_dispatch`) or automatically when relevant files are pushed to `dev`.
6. The NDK install step allows the Android SDK preview channel when resolving NDK `28.0.12433566`.

## Confirmed existing files in the target repository

The full GitHub repository already contains the Gradle wrapper (`gradlew`, `gradle/wrapper/gradle-wrapper.jar`, and properties), `modules/common`, `modules/fabric`, `modules/native`, and the `.gitmodules` entries for FreeType and glslang. They should not be recreated as empty placeholders. The workflow initializes the two required native submodules on the GitHub runner and installs JDK 25, Android NDK, CMake, and Ninja there.

## Apply (recommended)

1. Download this ZIP and extract it to its own folder, separate from the source repository.
2. In a terminal, run:

   ```bash
   bash /path/to/extracted-v15.1.4/apply-v15-overlay.sh /path/to/superresolutionarm64chatgpt
   ```

3. Review the target repository changes with `git -C /path/to/superresolutionarm64chatgpt diff`.
4. Commit and push the changes to branch `dev`. The Android build workflow is configured to run on relevant pushes to `dev`; it can also be started manually from GitHub Actions.
5. Download the artifact named `super-resolution-fabric-1.21.11-android-arm64` only after the workflow completes successfully.

## Build and verification stages

- Install Android NDK `28.0.12433566` and use Java 25.
- Initialize FreeType and glslang submodules.
- Configure and build the `arm64-v8a` JNI native library with optional desktop-only providers disabled.
- Verify ELF64/AArch64 and required JNI exports.
- Embed the native `.so` into the Fabric mod resources.
- Build `:fabric:remapJar` for Minecraft 1.21.11 and verify the embedded native file is byte-for-byte identical to the built `.so`.

## Important limitation

This patch does not contain the entire upstream project or a compiled Android binary. The GitHub Actions runner must successfully build the native library and Fabric JAR. A successful packaging workflow still does not prove the mod loads on the target Android device or that Vulkan/OpenGL external-memory interop and upscaling work at runtime. That requires testing in Zalith 2 on the target device.
