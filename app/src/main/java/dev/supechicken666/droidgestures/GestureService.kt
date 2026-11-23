/*
 * Copyright (C) 2026 SupeChicken666 <me@supechicken666.dev>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.supechicken666.droidgestures

import android.util.Log
import kotlin.system.exitProcess

class GestureService : IGestureService.Stub() {
    private external fun start(): Unit
    private external fun stop(): Unit
    override external fun setGestureCmd(gesture: String?, command: String?): Boolean

    init {
        System.loadLibrary("droidgestures")
        start()
    }

    override fun destroy() {
        stop()
        exitProcess(0)
    }

    override fun restart() {
        stop()
        start()
    }
}
