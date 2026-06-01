# BambuserCallsShopper · Android — Integration guide

The Android API is a one-to-one mirror of the iOS package's API, expressed in Kotlin and Compose idioms. The same mental model and event flow apply.

## 1. Add the dependency

Local development (project source available):
```kotlin
// settings.gradle.kts
includeBuild("../BambuserCallShopperSDKAndroid")

// app/build.gradle.kts
dependencies {
    implementation("com.bambuser:bambusercalls-shopper:1.0.0")
}
```

Or, while the artifact is unpublished, depend on the library module directly:
```kotlin
implementation(project(":bambusercalls-shopper"))
```

Once `./gradlew :bambusercalls-shopper:publishToMavenLocal` has run, add `mavenLocal()` to your `dependencyResolutionManagement` repositories and the implementation line resolves.

## 2. Manifest permissions

The host app must request these:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

…and at runtime ask for `CAMERA` + `RECORD_AUDIO` via `ActivityResultContracts.RequestMultiplePermissions` (or `accompanist-permissions`) before the user can start a call. The SDK auto-grants the WebView-level `PermissionRequest`, but Android still gates camera/mic at the OS level.

## 3. Configure

Only `orgId` is required. The rest have sensible defaults.

```kotlin
val config = BambuserCallConfiguration(
    orgId = "your-org-id",
    embedUrl = "https://one-to-one.bambuser.com/embed.js",
    floatingPipSize = DpSize(200.dp, 280.dp),  // default 180×260
    isInspectable = BuildConfig.DEBUG,
    autoExpandOnConnect = true,
    dismissOnCallEnd = true,
)
```

## 4. Bootstrap and wire the delegate

```kotlin
class MainActivity : ComponentActivity() {
    private val cart = CartStore()
    private val bambuserCall = BambuserCallController(
        configuration = BambuserCallConfiguration(orgId = "your-org-id"),
    )
    private val bridge = BambuserCallBridge(cart).also { bambuserCall.delegate = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    YourAppContent()
                    BambuserCallOverlay(controller = bambuserCall)
                }
            }
        }
    }
}
```

`BambuserCallOverlay` is empty until you call `bambuserCall.show()`, so it has no cost when no call is active. Place it last in the `Box` so it draws on top.

## 5. Implement the bridge

```kotlin
class BambuserCallBridge(private val cart: CartStore) : BambuserCallDelegate {

    override fun onEvent(controller: BambuserCallController, event: BambuserCallEvent) {
        when (event) {
            is BambuserCallEvent.NavigateTo -> {
                navigateToProduct(event.externalId)
                controller.notifyProductNavigation(externalId = event.externalId)
            }
            is BambuserCallEvent.Checkout -> switchToCartTab()
            is BambuserCallEvent.ShouldAddToCart -> {
                cart.add(sku = event.sku, quantity = event.quantity)
                controller.notify(callbackKey = event.callbackKey, info = true)
            }
            is BambuserCallEvent.ShouldUpdateCart -> {
                cart.setQuantity(sku = event.sku, quantity = event.quantity)
                controller.notify(callbackKey = event.callbackKey, info = true)
            }
            BambuserCallEvent.Close,
            is BambuserCallEvent.CallStateChanged,
            is BambuserCallEvent.PresentationChanged,
            is BambuserCallEvent.Other -> Unit
        }
    }

    override fun onError(controller: BambuserCallController, error: BambuserCallError) {
        Log.w("Bambuser", "error: $error")
    }
}
```

## 6. Start a call

```kotlin
Button(onClick = { bambuserCall.show() }) { Text("Talk to expert") }
```

With routing parameters:

```kotlin
bambuserCall.show(
    connectId = "expert-42",       // optional
    queue = "support-fashion",     // optional
)
```

Both are nil-safe — omitted from the JS embed config when null.

## 7. Echo navigations

Whenever the user lands on a PDP outside the call, tell the embed so its
product carousel stays in sync:

```kotlin
LaunchedEffect(product.id) {
    bambuserCall.notifyProductNavigation(externalId = product.id)
}
```

`notifyProductNavigation` is gated on `isCallActive` internally — safe to fire on every PDP composition.

## Public API at a glance

| Type | Purpose |
|---|---|
| `BambuserCallController` | Owns state (Compose-observable), lifecycle, and JS bridge. |
| `BambuserCallOverlay` | `@Composable` that renders the embed at the right frame. |
| `BambuserCallConfiguration` | `data class` of public knobs. |
| `BambuserCallDelegate` | Two-method interface: `onEvent`, `onError`. |
| `BambuserCallEvent` | Sealed class of every event the framework recognizes. |
| `BambuserCallError` | Sealed class for JS evaluation + payload-parse failures. |
| `CallState` | `Idle` / `Connecting` / `Connected` / `Ended`. |
| `PipPresentation` | `Minimized` / `Floating`. |

## What's not here yet

Product hydration (`provide-product-data` round-trip) is intentionally absent pending a redesign — same status as the iOS package. Inject your own JS handler on top of the embed and resolve via `controller.notify(callbackKey, info)` if you need it today.
