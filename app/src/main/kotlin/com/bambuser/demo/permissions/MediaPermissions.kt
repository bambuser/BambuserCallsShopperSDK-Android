package com.bambuser.demo.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner

/**
 * Force-ask CAMERA + RECORD_AUDIO at app launch, with a rationale
 * dialog + Settings redirect for the "permanently denied" case, and
 * an on-resume recheck so the app updates if the shopper grants
 * permissions via system Settings and returns.
 *
 * Drop `EnsureMediaPermissions()` at the top of your root Compose
 * tree. It's a no-op once both permissions are granted.
 */
@Composable
fun EnsureMediaPermissions() {
    val context  = LocalContext.current
    val activity = context.findActivity() ?: return
    val required = remember {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    fun granted(): Boolean = required.all { p ->
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }

    var showRationale by remember { mutableStateOf(false) }
    var showSettings  by remember { mutableStateOf(false) }
    var granted       by remember { mutableStateOf(granted()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        granted = results.values.all { it }
        if (!granted) {
            // If we can no longer show the rationale, the OS has
            // permanently denied — push the user to Settings.
            val canRationale = required.any {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
            if (canRationale) showRationale = true
            else              showSettings  = true
        }
    }

    // Fire once at launch.
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(required)
    }

    // Re-check when the app resumes (in case the shopper granted
    // permissions from system Settings and came back).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = granted()
                if (granted) { showRationale = false; showSettings = false }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Camera and microphone needed") },
            text = {
                Text(
                    "This demo uses your camera and microphone to " +
                    "connect you to a live expert. Without these " +
                    "permissions the call cannot start."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(required)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now") }
            },
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Permission required") },
            text = {
                Text(
                    "Camera and microphone are permanently denied. " +
                    "Open Settings to enable them."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettings = false
                    context.openAppSettings()
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("Cancel") }
            },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
