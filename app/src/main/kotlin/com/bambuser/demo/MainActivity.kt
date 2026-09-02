package com.bambuser.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bambuser.callsshopper.BambuserCallConfiguration
import com.bambuser.callsshopper.BambuserCallController
import com.bambuser.callsshopper.BambuserEnvironment
import com.bambuser.demo.cart.CartStore
import com.bambuser.demo.root.RootScreen

// ---------------------------------------------------------------------------
// Bambuser settings — fill these in before running the demo.
// ---------------------------------------------------------------------------

/**
 * Your Bambuser organization id. Fill in locally before running the
 * demo — do NOT commit a real value. The release validator
 * (Scripts/validate-release.sh) fails the pipeline if this is
 * non-empty on the develop branch.
 */
private const val DEMO_ORG_ID: String = ""

/**
 * Which Bambuser region this demo talks to.
 *
 *  - DEBUG builds:   `stageUS` (Bambuser-internal staging — URL is
 *                    gated by `BuildConfig.DEBUG` inside the SDK
 *                    and never lands in a release binary).
 *  - Release builds: swap to `US` (or `EU`).
 *
 * Mirrors the iOS demo's environment resolution.
 */
private val DEMO_ENVIRONMENT: BambuserEnvironment = BambuserEnvironment.stageUS

/** True once the org id is filled in. */
internal val isBambuserConfigured: Boolean get() = DEMO_ORG_ID.isNotEmpty()

class MainActivity : ComponentActivity() {

    private val cart = CartStore()

    // One controller for the app's lifetime — held by the Activity so
    // the overlay survives config changes (rotation, dark mode). Adapt
    // this: in a multi-activity app, hoist ownership into a
    // ViewModel / DI graph so the same controller instance is reused
    // wherever the overlay renders.
    private val bambuserCall = BambuserCallController(
        configuration = BambuserCallConfiguration(
            orgId = DEMO_ORG_ID,
            environment = DEMO_ENVIRONMENT,
        )
    )

    // Bridge implements BambuserCallDelegate + builds the handlers.
    // Kotlin doesn't have `weak var`, so we hold a strong reference
    // here in the Activity and assign it to `bambuserCall.delegate`.
    // Release order matters: null the delegate before dropping the
    // controller if you ever replace it mid-session.
    private val bridge = BambuserCallBridge(cart, bambuserCall).also {
        bambuserCall.delegate = it
        bambuserCall.handlers = it.buildHandlers()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    var showSetupAlert by remember { mutableStateOf(!isBambuserConfigured) }

                    // Force-ask CAMERA + RECORD_AUDIO at launch with a
                    // rationale + Settings-redirect fallback. No-op once
                    // permissions are held.
                    com.bambuser.demo.permissions.EnsureMediaPermissions()

                    RootScreen(cart = cart, bambuserCall = bambuserCall)

                    if (showSetupAlert) {
                        AlertDialog(
                            onDismissRequest = { showSetupAlert = false },
                            title = { Text("Bambuser credentials missing") },
                            text = {
                                Text(
                                    "Set DEMO_ORG_ID (and, if needed, DEMO_ENVIRONMENT) " +
                                    "at the top of MainActivity.kt before starting an " +
                                    "expert call."
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

}
