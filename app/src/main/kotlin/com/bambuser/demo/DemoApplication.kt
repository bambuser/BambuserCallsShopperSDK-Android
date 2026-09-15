package com.bambuser.demo

import android.app.Application
import com.bambuser.callsshopper.BambuserCallConfiguration
import com.bambuser.callsshopper.BambuserCallController
import com.bambuser.callsshopper.BambuserShopperSDK
import com.bambuser.demo.cart.CartStore

/**
 * Application-scoped state so a single [BambuserCallController]
 * instance is shared across Activities. That's the requirement for
 * the "retain WebView across Activities" flow: both [MainActivity]
 * and [SecondActivity] attach their overlay to the same controller,
 * and the SDK reparents the retained WebView between them.
 *
 * In a real app you'd typically use DI (Hilt @Singleton) instead of
 * a plain Application field.
 *
 * `DEMO_ORG_ID` / `DEMO_ENVIRONMENT` continue to live at file scope
 * in `MainActivity.kt` so the release validator's HEAD-vs-empty
 * check and any skip-worktree local overrides still work.
 */
class DemoApplication : Application() {

    /** Shopping cart shared across the two Activities. */
    val cart: CartStore by lazy { CartStore() }

    /** The one call controller for the app's lifetime. */
    val bambuserCall: BambuserCallController by lazy {
        BambuserCallController(
            configuration = BambuserCallConfiguration(
                orgId = DEMO_ORG_ID,
                environment = DEMO_ENVIRONMENT,
                // Opt-in: keep the widget WebView + WebRTC session
                // alive as the shopper navigates between Activities.
                // Without this, the call ends the moment the hosting
                // Activity is destroyed.
                retainWebViewAcrossActivities = true,
            )
        )
    }

    /** App-scope analytics handle. */
    val bambuserSDK: BambuserShopperSDK by lazy {
        BambuserShopperSDK(
            context = applicationContext,
            environment = DEMO_ENVIRONMENT,
            orgId = DEMO_ORG_ID,
        )
    }

    /**
     * Bridge outlives the compose tree; created once and reused so
     * both Activities' overlays report events to the same handler set.
     */
    val bridge: BambuserCallBridge by lazy {
        BambuserCallBridge(cart, bambuserCall).also {
            bambuserCall.delegate = it
            bambuserCall.handlers = it.buildHandlers()
        }
    }
}
