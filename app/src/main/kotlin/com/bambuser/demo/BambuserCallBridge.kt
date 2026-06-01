package com.bambuser.demo

import android.util.Log
import com.bambuser.callsshopper.BambuserCallController
import com.bambuser.callsshopper.BambuserCallDelegate
import com.bambuser.callsshopper.BambuserCallError
import com.bambuser.callsshopper.BambuserCallEvent
import com.bambuser.demo.cart.CartStore
import com.bambuser.demo.catalog.ProductCatalog
import com.bambuser.demo.root.NavigationBridge

/**
 * Bridges the SDK's event stream to the demo's navigation and cart.
 */
class BambuserCallBridge(private val cart: CartStore) : BambuserCallDelegate {

    override fun onEvent(controller: BambuserCallController, event: BambuserCallEvent) {
        when (event) {
            is BambuserCallEvent.NavigateTo -> handleNavigateTo(event.externalId, controller)

            is BambuserCallEvent.Checkout -> NavigationBridge.onSwitchToCart?.invoke()

            is BambuserCallEvent.ShouldAddToCart -> applyCart(
                sku = event.sku,
                quantity = event.quantity,
                callbackKey = event.callbackKey,
                controller = controller,
            )

            is BambuserCallEvent.ShouldUpdateCart -> applyCart(
                sku = event.sku,
                quantity = event.quantity,
                callbackKey = event.callbackKey,
                controller = controller,
            )

            BambuserCallEvent.Close,
            is BambuserCallEvent.CallStateChanged,
            is BambuserCallEvent.PresentationChanged,
            is BambuserCallEvent.Other -> Unit
        }
    }

    override fun onError(controller: BambuserCallController, error: BambuserCallError) {
        Log.w(TAG, "BambuserCall error: $error")
    }

    private fun handleNavigateTo(sku: String, controller: BambuserCallController) {
        val product = ProductCatalog.product(forSku = sku)
        if (product == null) {
            Log.w(TAG, "no product for externalId='$sku'")
            return
        }
        val nav = NavigationBridge.productsNav ?: return
        nav.navigate("detail/$sku") {
            launchSingleTop = true
        }
        controller.notifyProductNavigation(externalId = sku)
    }

    private fun applyCart(
        sku: String,
        quantity: Int,
        callbackKey: String,
        controller: BambuserCallController,
    ) {
        if (sku.isEmpty()) {
            controller.notify(
                callbackKey = callbackKey,
                info = "{ success: false, reason: 'missing-sku' }",
            )
            return
        }
        cart.setQuantity(sku = sku, quantity = quantity)
        controller.notify(callbackKey = callbackKey, info = true)
    }

    companion object { private const val TAG = "BambuserCallBridge" }
}
