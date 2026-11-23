/*
 * Copyright (C) 2026 SupeChicken666 <me@supechicken666.dev>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

#include <filesystem>
#include <memory>
#include <optional>
#include <jni.h>

#include "GestureProcesser.h"
#include "InputDevice.h"
#include "Log.h"

static std::unique_ptr<GestureProcesser> gestureProcesser;

static void getTouchpadDevice(std::shared_ptr<InputDevice>& device) {
    device = std::make_shared<InputDevice>();

    for (const auto& node : std::filesystem::directory_iterator("/dev/input")) {
        if (!node.is_character_file()) continue;

        if (device->openDevice(node.path()) != 0) {
            device->closeDevice();
            continue;
        }

        if (device->isTouchpad()) {
            Log::info("Touchpad found: {}", node.path().string());
            return;
        }

        device->closeDevice();
    }

    if (device->fd == -1) {
        Log::err("No valid touchpad device found");
        device = nullptr;
        return;
    }
}

extern "C" JNIEXPORT void JNICALL Java_dev_supechicken666_droidgestures_GestureService_start(
    JNIEnv* /* env */,
    jobject /* this */
) {
    std::shared_ptr<InputDevice> device;
    getTouchpadDevice(device);

    if (device == nullptr) return;

    gestureProcesser = std::make_unique<GestureProcesser>(device);
    gestureProcesser->start();
}

extern "C" JNIEXPORT void JNICALL Java_dev_supechicken666_droidgestures_GestureService_stop(
    JNIEnv* /* env */,
    jobject /* this */
) {
    if (gestureProcesser) gestureProcesser->stop();
}

extern "C" JNIEXPORT jboolean JNICALL Java_dev_supechicken666_droidgestures_GestureService_setGestureCmd(
    JNIEnv* env,
    jobject /* this */,
    jstring gesture,
    jstring command
) {
    bool ret = true;

    const char *gestureString = gesture ? env->GetStringUTFChars(gesture, nullptr) : nullptr,
               *commandString = command ? env->GetStringUTFChars(command, nullptr) : nullptr;

    // Reset all commands if gesture is null
    if (gestureString == nullptr) {
        Log::info("Reset all gesture commands", gestureString);
        for (auto& [k, v] : gestureProcesser->customGestureCmds) v = std::nullopt;
        goto cleanup;
    }

    // Set command for gesture
    if (
        auto it = gestureProcesser->customGestureCmds.find(gestureString);
        it == gestureProcesser->customGestureCmds.end()
    ) {
        Log::err("Gesture {} does not exist", gestureString);
        ret = false;
        goto cleanup;
    } else {
        it->second = command ? std::optional<std::string>(commandString) : std::nullopt;
    }

    if (command) {
        Log::info("Configured command for gesture {}: {}", gestureString, commandString);
    } else {
        Log::info("Command unset for gesture {}", gestureString);
    }

cleanup:
    if (command) env->ReleaseStringUTFChars(command, commandString);
    if (gesture) env->ReleaseStringUTFChars(gesture, gestureString);
    return ret;
}
