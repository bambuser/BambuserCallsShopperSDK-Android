package com.bambuser.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.bambuser.callsshopper.BambuserCallConfiguration
import com.bambuser.callsshopper.BambuserCallController
import com.bambuser.demo.cart.CartStore
import com.bambuser.demo.root.RootScreen

// MARK: - Bambuser settings — fill these in before running the demo.

/** Your Bambuser organization id. */
private const val DEMO_ORG_ID: String = ""

/**
 * The Bambuser embed script URL.
 *
 * - Production:    https://one-to-one.bambuser.com/embed.js
 * - Production EU: https://one-to-one.bambuser.com/eu/embed.js
 * - Labs branch:   https://labs.bambuser.com/one-to-one/gitlab-mr/<id>/embed.js
 */
private const val DEMO_EMBED_URL: String = ""

/** True once both fields above are filled in. */
internal val isBambuserConfigured: Boolean
    get() = DEMO_ORG_ID.isNotEmpty() && DEMO_EMBED_URL.isNotEmpty()

class MainActivity : ComponentActivity() {

    private val cart = CartStore()
    private val bambuserCall = BambuserCallController(
        configuration = BambuserCallConfiguration(
            orgId = DEMO_ORG_ID,
            embedUrl = DEMO_EMBED_URL.ifEmpty { BambuserCallConfiguration.DEFAULT_EMBED_URL },
        )
    )
    private val bridge = BambuserCallBridge(cart).also { bambuserCall.delegate = it }

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* nothing — granted permissions take effect on next call attempt */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureMediaPermissions()
        setContent {
            MaterialTheme {
                Surface {
                    var showSetupAlert by remember { mutableStateOf(!isBambuserConfigured) }
                    LaunchedEffect(Unit) { /* one-shot guard */ }

                    RootScreen(cart = cart, bambuserCall = bambuserCall)

                    if (showSetupAlert) {
                        AlertDialog(
                            onDismissRequest = { showSetupAlert = false },
                            title = { Text("Bambuser credentials missing") },
                            text = {
                                Text(
                                    "Set DEMO_ORG_ID and DEMO_EMBED_URL at the top of " +
                                    "MainActivity.kt before starting an expert call."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { showSetupAlert = false }) { Text("OK") }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun ensureMediaPermissions() {
        val needed = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
        if (needed.isNotEmpty()) permissionRequest.launch(needed)
    }
}
