package com.bambuser.demo.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bambuser.callsshopper.BambuserShopperSDK
import com.bambuser.demo.catalog.DemoProduct
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    cart: CartStore,
    bambuserSDK: BambuserShopperSDK,
) {
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text("Cart") }) }) { padding ->
        if (cart.orderedLines.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            Column(Modifier.padding(padding)) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    items(cart.orderedLines, key = { it.first.id }) { (product, qty) ->
                        CartRow(
                            product = product,
                            quantity = qty,
                            onIncrement = { cart.setQuantity(product.id, qty + 1) },
                            onDecrement = { cart.setQuantity(product.id, qty - 1) },
                        )
                    }
                }
                Divider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Subtotal", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$${"%.2f".format(cart.subtotal)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                // Adapt this: fire the shopper-events `purchase` payload
                // to Bambuser at the moment your app records the sale
                // (order-confirmation screen, server callback). Here we
                // do it on the demo "Complete purchase" tap. Bambuser
                // attributes this event back to the current — or most
                // recent — Video Consultation call via the persisted
                // userId + LCTE, so it works even after the shopper
                // ended the call and kept browsing.
                Button(
                    onClick = {
                        val lines = cart.orderedLines
                        val subtotal = cart.subtotal
                        val products = lines.map { (product, qty) ->
                            mapOf(
                                "id" to product.id,
                                "quantity" to qty,
                                "name" to product.name,
                                "image" to product.imageUrl,
                                "price" to product.price,
                                "currency" to "USD",
                                "brand" to product.brand,
                            )
                        }
                        val transaction = mapOf(
                            "id" to UUID.randomUUID().toString(),
                            "currency" to "USD",
                            "subtotal" to subtotal,
                            "total" to subtotal,
                            "tax" to 0.0,
                            "shippingCost" to 0.0,
                        )
                        scope.launch {
                            runCatching {
                                bambuserSDK.track(
                                    event = "purchase",
                                    data = mapOf(
                                        "transaction" to transaction,
                                        "products" to products,
                                    ),
                                )
                            }
                        }
                        cart.clear()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text("Complete purchase")
                }

                TextButton(
                    onClick = { cart.clear() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text("Empty cart", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Your cart is empty.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add items from the Products tab or via the expert call.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CartRow(
    product: DemoProduct,
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = product.imageUrl,
            contentDescription = product.name,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                product.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
            )
            Text(
                "$${"%.2f".format(product.price)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease")
            }
            Text("$quantity", style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = onIncrement) {
                Icon(Icons.Filled.Add, contentDescription = "Increase")
            }
        }
    }
}
