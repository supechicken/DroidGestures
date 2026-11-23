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
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import rikka.shizuku.Shizuku

val Context.dataStore by preferencesDataStore(name = "GestureSettings")

class MainActivity : ComponentActivity() {
    private var shizukuConnected by mutableStateOf(false)
    private var shizukuAuthorized by mutableStateOf(false)
    private var gestureService by mutableStateOf<IGestureService?>(null)

    private var statusMessage by mutableStateOf("Unable to connect to Shizuku (is Shizuku installed?)")
    private var actionBtnText by mutableStateOf("Authorize")

    fun applySavedSettings(ctx: Context, scope: CoroutineScope) {
        if (gestureService == null) return;

        scope.launch {
            gestureService?.setGestureCmd(null, null)

            for ((k, v) in ctx.dataStore.data.first().asMap()) {
                when (v) {
                    is String -> {
                        Log.i("DroidGestures", "Applying command for gesture ${k.name}: $v")
                        gestureService?.setGestureCmd(k.name, v)
                    }
                }
            }
        }
    }

    fun onActionBtnClick(ctx: Context, scope: CoroutineScope) {
        when {
            !shizukuAuthorized -> {
                Shizuku.requestPermission(ShizukuWrapper.PERMISSION_REQUEST_CODE)
            }
            gestureService == null -> {
                ShizukuWrapper.bindGestureService()
            }
            else -> {
                ShizukuWrapper.unbindGestureService()
            }
        }
    }

    @Composable
    fun createGestureItem(ctx: Context, fingerCount: Int, direction: String) {
        val scope = rememberCoroutineScope()
		var showDialog by rememberSaveable { mutableStateOf(false) }

        val gestureName = "${fingerCount}FingerSwipe${direction.replaceFirstChar { it.uppercase() }}"
        val customCommand by remember(gestureName) {
            ctx.dataStore.data
                .map { p -> p[stringPreferencesKey(gestureName)] }
                .distinctUntilChanged()
        }.collectAsState(initial = null)

		var customCommandEditorValue by rememberSaveable(customCommand) {
            mutableStateOf(customCommand ?: "")
        }

		ListItem(
		    supportingContent = {
                if (customCommand.isNullOrBlank()) {
                    Text("Default (handled by Android)")
                } else {
                    Text("Run shell command: $customCommand")
                }
            },
			colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
			modifier = Modifier
				.fillMaxWidth()
				.clip(RoundedCornerShape(4.dp))
				.background(MaterialTheme.colorScheme.surfaceBright)
				.clickable { showDialog = true }
		) {
            Text("Swipe $direction")
        }

        if (showDialog) {
            AlertDialog(
			    onDismissRequest = { showDialog = false },
			    title = { Text("Action for $fingerCount-finger swipe $direction") },
			    text = {
					OutlinedTextField(
						value = customCommandEditorValue,
						label = { Text("Shell command to run") },
                        supportingText = {
                            Column {
                                Text("Use 'input keycombination' for keybinds")
                                Text("Use 'am start' for launching apps")
                                Text("")
                                Text("Leave empty for default action")
                            }
                        },
                        shape = RoundedCornerShape(15.dp),
						onValueChange = { customCommandEditorValue = it },
                        modifier = Modifier.fillMaxWidth(),
						singleLine = true
					)
				},
				confirmButton = {
					TextButton(onClick = {
                        val unsetCmd = customCommandEditorValue.isBlank()

                        // Save changes
                        scope.launch {
                            ctx.dataStore.edit { settings ->
                                if (unsetCmd) {
                                    settings.remove(stringPreferencesKey(gestureName))
                                } else {
                                    settings[stringPreferencesKey(gestureName)] = customCommandEditorValue
                                }
                            }
                            applySavedSettings(ctx, scope)
                        }

                        if (unsetCmd) {
                            Log.i("DroidGestures", "Command unset for $fingerCount-finger swipe $direction")
                        } else {
						    Log.i("DroidGestures", "Configured command for $fingerCount-finger swipe $direction: $customCommand")
                        }

                        showDialog = false
					}) { Text("Done") }
			    }
	        )
        }
    }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

        ShizukuWrapper.registerListeners()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ShizukuWrapper.statusChangeFlow().collect { (connected, authorized, service) ->
                    shizukuConnected = connected
                    shizukuAuthorized = authorized
                    gestureService = service

                    statusMessage = when {
                        gestureService != null -> "DroidGesture service is running"
                        shizukuAuthorized -> "DroidGesture service stopped"
                        shizukuConnected -> "Shizuku permission denied"
                        else -> "Unable to connect to Shizuku (is Shizuku installed?)"
                    }

                    actionBtnText = when {
                        gestureService != null -> "Stop"
                        shizukuAuthorized -> "Start"
                        else -> "Authorize"
                    }

                    if (gestureService != null) applySavedSettings(this@MainActivity, lifecycleScope)
                }
            }
        }

		setContent {
			val ctx = LocalContext.current
			val colorScheme = when {
				isSystemInDarkTheme() -> dynamicDarkColorScheme(ctx)
				else -> dynamicLightColorScheme(ctx)
			}

			MaterialTheme(colorScheme = colorScheme) {
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = MaterialTheme.colorScheme.surfaceContainer
				) {
					Column(
                        modifier = Modifier
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
						Surface(
							modifier = Modifier.fillMaxWidth(),
							shape = RoundedCornerShape(20.dp),
							color = MaterialTheme.colorScheme.secondaryContainer,
							contentColor = MaterialTheme.colorScheme.onSecondaryContainer
						) {
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(16.dp),
								verticalAlignment = Alignment.CenterVertically
							) {
								Text(text = statusMessage)
								Spacer(modifier = Modifier.weight(1f))
								Button(
                                    enabled = shizukuConnected,
									onClick = { onActionBtnClick(ctx, lifecycleScope) }
								) { Text(actionBtnText) }
							}
						}

						(3..4).forEach { i ->
						    Spacer(modifier = Modifier.height(12.dp))
                            Column {
                                Text(
                                    text = "Actions for $i-finger swipe",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                )
							    Surface(
								    modifier = Modifier.fillMaxWidth(),
								    shape = RoundedCornerShape(20.dp),
								    color = androidx.compose.ui.graphics.Color.Transparent
							    ) {
								    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
									    createGestureItem(ctx, i, "up")
									    createGestureItem(ctx, i, "down")
									    createGestureItem(ctx, i, "left")
									    createGestureItem(ctx, i, "right")
								    }
							    }
                            }
						}
					}
				}
			}
		}
	}

	override fun onDestroy() {
		ShizukuWrapper.unregisterListeners()
		super.onDestroy()
	}
}
