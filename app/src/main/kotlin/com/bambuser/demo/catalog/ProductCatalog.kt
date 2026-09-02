package com.bambuser.demo.catalog

/**
 * The five demo SKUs the app sells. SKUs match the ids the Bambuser
 * embed will hand back via `navigate-to` and cart events, so a lookup
 * with the bare id resolves directly.
 *
 * Adapt this: replace with your product-catalog client — a Room DAO,
 * a repository backed by your storefront API, an in-memory feed
 * pushed from server config, etc. Everything downstream
 * (`BambuserCallBridge.handleNavigate`, `provideProductData`,
 * `provideSearchData`) reads via `ProductCatalog.all` and
 * `ProductCatalog.product(forSku)`, so the surface you need to
 * preserve is small.
 */
object ProductCatalog {
    val all: List<DemoProduct> = listOf(
        DemoProduct(
            id = "1791",
            name = "3-Pack Resistance Bands",
            brand = "Bambuser",
            category = "Sports",
            price = 30.0,
            currency = "USD",
            inStock = true,
            description = "Crafted from premium, durable rubber, our FlexFit Resistance Bands provide the perfect amount of tension for a challenging and effective workout. Whether you're looking to build strength, increase flexibility, or improve your overall fitness level, these bands are a must-have addition to your exercise routine.",
            imageUrl = "https://demo.bambuser.shop/wp-content/uploads/2023/06/Sport_4.png",
            url = "https://demo.bambuser.shop/product/1791/3-pack-resistance-bands/",
        ),
        DemoProduct(
            id = "1269",
            name = "Smartphone",
            brand = "Bambuser",
            category = "Consumer Electronics",
            price = 499.0,
            currency = "USD",
            inStock = true,
            description = "Experience stunning visuals and vibrant colors on our smartphone's immersive display. With its high-resolution screen and edge-to-edge design, every image, video, and game is brought to life with incredible clarity and detail.",
            imageUrl = "https://demo.bambuser.shop/wp-content/uploads/2023/05/ConsumerElectronics_5.png",
            url = "https://demo.bambuser.shop/product/1269/smartphone/",
        ),
        DemoProduct(
            id = "1234",
            name = "The Classic Eyeliner",
            brand = "Bambuser",
            category = "Beauty",
            price = 18.0,
            currency = "USD",
            inStock = true,
            description = "Unleash your inner artist and create stunning eye looks with our revolutionary eyeliner. Experience effortless precision and long-lasting definition with our smudge-proof formula, designed to elevate your eye game to new heights.",
            imageUrl = "https://demo.bambuser.shop/wp-content/uploads/2023/05/Makeup_4.png",
            url = "https://demo.bambuser.shop/product/1234/the-classic-eyeliner/",
        ),
        DemoProduct(
            id = "1233",
            name = "Precision Eyelash Curler",
            brand = "Bambuser",
            category = "Beauty",
            price = 15.0,
            currency = "USD",
            inStock = true,
            description = "Our Precision Curl Eyelash Curler features a sleek and ergonomic design, allowing for a comfortable grip and precise control. The specially crafted silicone pad gently lifts and curls your lashes from the base to the tip.",
            imageUrl = "https://demo.bambuser.shop/wp-content/uploads/2023/05/Makeup_6.png",
            url = "https://demo.bambuser.shop/product/1233/precision-eyelash-curler/",
        ),
        DemoProduct(
            id = "1810",
            name = "8-pieces Rainbow",
            brand = "Bambuser",
            category = "Kids",
            price = 16.0,
            currency = "USD",
            inStock = true,
            description = "This playful rainbow consists of bows in different colors and sizes. Stack them in the right order, and a nice rainbow emerges. Or experiment and make your own unique designs.",
            imageUrl = "https://demo.bambuser.shop/wp-content/uploads/2023/06/Kids_1.1-1.png",
            additionalImages = listOf(
                "https://demo.bambuser.shop/wp-content/uploads/2023/06/Kids_1.2-1.png",
                "https://demo.bambuser.shop/wp-content/uploads/2023/06/Kids_1.3-1.png",
            ),
            url = "https://demo.bambuser.shop/product/1810/8-pieces-rainbow/",
        ),
    )

    /** O(1) lookup by SKU. */
    val bySku: Map<String, DemoProduct> = all.associateBy { it.id }

    fun product(forSku: String): DemoProduct? = bySku[forSku]
}
