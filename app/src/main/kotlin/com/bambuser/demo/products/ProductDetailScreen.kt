package com.bambuser.demo.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bambuser.callsshopper.BambuserCallController
import com.bambuser.demo.cart.CartStore
import com.bambuser.demo.catalog.DemoProduct
import com.bambuser.demo.isBambuserConfigured

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    product: DemoProduct,
    cart: CartStore,
    bambuserCall: BambuserCallController,
    onBack: () -> Unit,
) {
    var addedToCart by remember { mutableStateOf(cart.quantity(forSku = product.id) > 0) }
    var showSetupAlert by remember { mutableStateOf(false) }

    // Echo the navigation back to the embed when the user lands on a PDP.
    LaunchedEffect(product.id) {
        bambuserCall.notifyProductNavigation(externalId = product.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(product.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    product.brand.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(product.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    "$${"%.2f".format(product.price)}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                product.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    cart.add(sku = product.id, quantity = 1)
                    addedToCart = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (addedToCart) Icons.Filled.Check else Icons.Outlined.ShoppingCart,
                    contentDescription = null
                )
                Text(
                    text = if (addedToCart) "  Added to Cart" else "  Add to Cart",
                )
            }

            OutlinedButton(
                onClick = {
                    if (isBambuserConfigured) bambuserCall.show()
                    else showSetupAlert = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PersonPin, contentDescription = null)
                Text("  Talk to expert")
            }
        }
    }

    if (showSetupAlert) {
        AlertDialog(
            onDismissRequest = { showSetupAlert = false },
            title = { Text("Bambuser credentials missing") },
            text = {
                Text(
                    "Set demoOrgId and demoEmbedUrlString at the top of " +
                    "MainActivity.kt before starting an expert call."
                )
            },
            confirmButton = {
                TextButton(onClick = { showSetupAlert = false }) { Text("OK") }
            },
        )
    }
}
