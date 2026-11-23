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

package dev.supechicken666.droidgestures

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

import rikka.shizuku.Shizuku

object ShizukuWrapper {
    const val PERMISSION_REQUEST_CODE = 1001

    private val shizukuConnected = MutableStateFlow(false)
    private val shizukuAuthorized = MutableStateFlow(false)

	private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        shizukuConnected.value = true
        shizukuAuthorized.value = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

        // Connect to existing service if it is already running
        if (shizukuAuthorized.value && Shizuku.peekUserService(gestureServiceArgs, gestureServiceConnection) != -1) {
            Shizuku.bindUserService(gestureServiceArgs, gestureServiceConnection)
        }
    }

	private val binderDeadListener = Shizuku.OnBinderDeadListener {
        shizukuConnected.value = false
        shizukuAuthorized.value = false
        gestureService.value = null
    }

	private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        shizukuAuthorized.value = grantResult == PackageManager.PERMISSION_GRANTED
    }

    private val gestureService = MutableStateFlow<IGestureService?>(null)
    private val gestureServiceArgs = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, GestureService::class.java.name))
        .daemon(true)
        .processNameSuffix("shizuku_worker")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val gestureServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            gestureService.value = IGestureService.Stub.asInterface(service)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            gestureService.value = null
        }
    }

    fun registerListeners() {
		Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
		Shizuku.addBinderDeadListener(binderDeadListener)
		Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }

    fun unregisterListeners() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    fun statusChangeFlow(): Flow<Triple<Boolean, Boolean, IGestureService?>> {
        return combine(shizukuConnected, shizukuAuthorized, gestureService) { connected, authorized, service ->
            Triple(connected, authorized, service)
        }
    }

    fun bindGestureService() {
        Shizuku.bindUserService(gestureServiceArgs, gestureServiceConnection)
    }

    fun unbindGestureService() {
        Shizuku.unbindUserService(gestureServiceArgs, gestureServiceConnection, true)
    }
}
