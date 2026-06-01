plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.bambuser.callsshopper"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
}

// Maven publication. Local development: `./gradlew :bambusercalls-shopper:publishToMavenLocal`
// Remote: configure `publishing.repositories` with the target Maven repo.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.bambuser"
            artifactId = "bambusercalls-shopper"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("BambuserCalls-shopper")
                description.set("Native Android wrapper for Bambuser's one-to-one video consultation embed.")
                url.set("https://github.com/bambuser/BambuserCallShopperSDKAndroid")
                licenses {
                    license {
                        name.set("Proprietary")
                        distribution.set("repo")
                    }
                }
            }
        }
    }
}
