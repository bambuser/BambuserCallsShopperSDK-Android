package com.bambuser.demo.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bambuser.callsshopper.BambuserCallController
import com.bambuser.callsshopper.BambuserCallOverlay
import com.bambuser.demo.cart.CartScreen
import com.bambuser.demo.cart.CartStore
import com.bambuser.demo.catalog.ProductCatalog
import com.bambuser.demo.products.ProductDetailScreen
import com.bambuser.demo.products.ProductsListScreen

@Composable
fun RootScreen(
    cart: CartStore,
    bambuserCall: BambuserCallController,
    productsNavController: NavHostController = rememberNavController(),
) {
    var selectedTab by remember { mutableStateOf(DemoTab.Products) }

    // Expose the products nav controller so the bridge can push PDPs.
    NavigationBridge.productsNav = productsNavController
    NavigationBridge.onSwitchToCart = { selectedTab = DemoTab.Cart }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    DemoTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = {
                                when (tab) {
                                    DemoTab.Products -> Icon(Icons.Filled.GridView, contentDescription = "Products")
                                    DemoTab.Cart -> {
                                        BadgedBox(
                                            badge = {
                                                if (cart.totalItems > 0) Badge { Text(cart.totalItems.toString()) }
                                            }
                                        ) {
                                            Icon(Icons.Filled.ShoppingCart, contentDescription = "Cart")
                                        }
                                    }
                                }
                            },
                            label = { Text(tab.title) },
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (selectedTab) {
                    DemoTab.Products -> NavHost(
                        navController = productsNavController,
                        startDestination = "list",
                    ) {
                        composable("list") {
                            ProductsListScreen { product ->
                                productsNavController.navigate("detail/${product.id}")
                            }
                        }
                        composable("detail/{sku}") { backStack ->
                            val sku = backStack.arguments?.getString("sku").orEmpty()
                            val product = ProductCatalog.product(forSku = sku)
                            if (product != null) {
                                ProductDetailScreen(
                                    product = product,
                                    cart = cart,
                                    bambuserCall = bambuserCall,
                                    onBack = { productsNavController.popBackStack() }
                                )
                            }
                        }
                    }

                    DemoTab.Cart -> CartScreen(cart)
                }
            }
        }

        // Overlay sits above the Scaffold so PiP floats over both tabs.
        // On Android 15+ / target SDK 35+, edge-to-edge is enforced,
        // so the overlay must respect the display safe area itself —
        // otherwise the widget's top-bar close button slides under
        // the status bar / camera cutout. `safeDrawingPadding()` pulls
        // in the status bar, nav bar, IME and cutout insets in one go.
        BambuserCallOverlay(
            controller = bambuserCall,
            modifier = Modifier.safeDrawingPadding(),
        )
    }
}

enum class DemoTab(val title: String) {
    Products("Products"),
    Cart("Cart");
}

/**
 * Shared sink the [com.bambuser.demo.BambuserCallBridge] writes to so it
 * can push PDPs and switch tabs from outside the composition.
 */
object NavigationBridge {
    var productsNav: NavHostController? = null
    var onSwitchToCart: (() -> Unit)? = null
}
