package com.bambuser.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bambuser.callsshopper.BambuserEnvironment
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
internal const val DEMO_ORG_ID: String = ""

/**
 * Which Bambuser region this demo talks to. Change to `EU` if your
 * workspace is on the EU cluster.
 */
internal val DEMO_ENVIRONMENT: BambuserEnvironment = BambuserEnvironment.US

/** True once the org id is filled in. */
internal val isBambuserConfigured: Boolean get() = DEMO_ORG_ID.isNotEmpty()

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DemoApplication
        // Touch the bridge so it wires up the controller before the
        // first show(). Cheap on subsequent Activity creates.
        app.bridge

        setContent {
            MaterialTheme {
                Surface {
                    var showSetupAlert by remember { mutableStateOf(!isBambuserConfigured) }
                    val context = LocalContext.current

                    // Force-ask CAMERA + RECORD_AUDIO at launch with a
                    // rationale + Settings-redirect fallback. No-op once
                    // permissions are held.
                    com.bambuser.demo.permissions.EnsureMediaPermissions()

                    Box(Modifier.fillMaxSize()) {
                        RootScreen(
                            cart = app.cart,
                            bambuserCall = app.bambuserCall,
                            bambuserSDK = app.bambuserSDK,
                        )

                        // "Open Second Activity" demo button, pinned
                        // top-start so it sits above the products list
                        // but below the widget overlay (RootScreen
                        // renders the overlay after this Box's content
                        // — but that's rendered inside RootScreen's own
                        // Box, so it lands on top of this button too).
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(context, SecondActivity::class.java)
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .safeDrawingPadding()
                                .padding(horizontal = 8.dp),
                        ) {
                            Text("→ Second Activity")
                        }
                    }

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
