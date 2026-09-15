package com.bambuser.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambuser.callsshopper.BambuserCallOverlay

/**
 * A second Activity to demonstrate the retain-WebView-across-Activities
 * flow.
 *
 * The visual content is intentionally different from [MainActivity] so
 * you can tell the two Activities apart when navigating between them
 * — but the [BambuserCallOverlay] renders on top of both, sharing the
 * same singleton [BambuserCallController] on [DemoApplication].
 *
 * With `retainWebViewAcrossActivities = true`, an active call started
 * in `MainActivity` keeps rendering while the shopper is on this
 * screen; the widget WebView is reparented into this Activity's
 * compose tree instead of being destroyed and re-created.
 */
class SecondActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DemoApplication

        setContent {
            MaterialTheme {
                Surface(color = Color(0xFFF5F5F0)) {
                    Box(Modifier.fillMaxSize()) {
                        SecondScreen(onBack = { finish() })

                        // Same overlay wiring as MainActivity — pulls
                        // the shared controller off DemoApplication.
                        // When retention is on, this composition
                        // reparents the retained WebView instead of
                        // creating a fresh one.
                        BambuserCallOverlay(
                            controller = app.bambuserCall,
                            modifier = Modifier.safeDrawingPadding(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEDE7F6))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Second Activity",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF311B92),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "If a Bambuser call was running in MainActivity, it should still be visible " +
                "(and audible) here — the same WebView instance was reparented into this " +
                "Activity's compose tree.",
            color = Color(0xFF311B92),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) {
            Text("Back to Main")
        }
    }
}
