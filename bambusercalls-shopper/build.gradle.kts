import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
}

// Pull `currentBuildVersion` + `currentGitBranch` from the shared helper.
apply(from = "$rootDir/gradle/utils.gradle.kts")

val currentVersion: String = project.extra["currentBuildVersion"] as String
val currentGitBranch: String = project.extra["currentGitBranch"] as String

// Internal GitLab builds are versioned <version>-dev.N.
// Pass -PdevBuildNumber=N when publishing to the GitLab registry; omit
// it for Repsy releases.
val devBuildNumber: String? = project.findProperty("devBuildNumber") as String?
val publishVersion: String =
    if (devBuildNumber != null) "$currentVersion-dev.$devBuildNumber" else currentVersion

// local.properties is optional — publish credentials read from env vars
// (`REPSY_USERNAME`, `REPSY_PASSWORD`, `GITLAB_TOKEN`) as a fallback for CI.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.bambuser.callsshopper"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        compose = true
        // Enables the generated BuildConfig class — used by
        // BambuserEnvironment.stageUS to gate the staging URL to
        // debug builds only, mirroring iOS's `#if DEBUG`.
        buildConfig = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.webkit)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

// ============================================================
// Maven publishing — mirrors the pattern from bambuser-commerce-sdk-android.
//
// Two repositories:
//   - Repsy: client releases (X.Y.Z)
//   - GitLab: internal dev builds (X.Y.Z-dev.N)
//
// Tasks:
//   ./gradlew :bambusercalls-shopper:publishCallsShopperSdkAndroidPublicationToMavenLocal
//   ./gradlew :bambusercalls-shopper:publishCallsShopperSdkAndroidPublicationToRepsyRepository
//   ./gradlew :bambusercalls-shopper:publishCallsShopperSdkAndroidPublicationToGitLabRepository -PdevBuildNumber=N
// ============================================================
project.afterEvaluate {
    // Remote repos are opt-in: they only register when their config
    // is present. That way `assembleDebug` (and `publishToMavenLocal`)
    // never touches Repsy / GitLab, and the build doesn't fail when
    // the URLs / credentials are unset.
    val repsyUrl: String? = localProperties.getProperty("repsyUrl")
        ?: System.getenv("REPSY_URL")
    val gitlabProjectId: String? = localProperties.getProperty("gitlabProjectId")
        ?: System.getenv("GITLAB_PROJECT_ID")

    publishing {
        repositories {
            if (repsyUrl != null) {
                maven {
                    name = "Repsy"
                    url = uri(repsyUrl)
                    credentials {
                        username = localProperties.getProperty("repsyUsername")
                            ?: System.getenv("REPSY_USERNAME")
                        password = localProperties.getProperty("repsyPassword")
                            ?: System.getenv("REPSY_PASSWORD")
                    }
                }
            }

            if (gitlabProjectId != null) {
                maven {
                    name = "GitLab"
                    url = uri("https://gitlab.bambuser.com/api/v4/projects/$gitlabProjectId/packages/maven")
                    credentials(HttpHeaderCredentials::class) {
                        val ciJobToken = System.getenv("CI_JOB_TOKEN")
                        if (ciJobToken != null) {
                            name = "Job-Token"
                            value = ciJobToken
                        } else {
                            name = "Private-Token"
                            value = localProperties.getProperty("gitlabToken")
                                ?: System.getenv("GITLAB_TOKEN")
                        }
                    }
                    authentication {
                        create("header", HttpHeaderAuthentication::class)
                    }
                }
            }
        }

        publications {
            create<MavenPublication>("callsShopperSdkAndroid") {
                groupId = "com.bambuser"
                artifactId = "calls-shopper-sdk-android"
                version = publishVersion
                from(components.findByName("release"))

                pom {
                    name.set("BambuserCallsShopperSDK-Android")
                    description.set("Native Android SDK for embedding Bambuser one-to-one video-call widgets into a shopping app")
                    url.set("https://github.com/bambuser/BambuserCallsShopperSDK-Android")
                    licenses {
                        license {
                            name.set("The Apache Software License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    organization {
                        name.set("Bambuser AB")
                        url.set("https://bambuser.com/")
                    }
                }
            }
        }
    }

    // Enforce the version conventions:
    // Repsy releases are plain Major.Minor.Patch, GitLab internal
    // builds are Major.Minor.Patch-dev.N. Same guardrail as the
    // commerce SDK.
    tasks.withType<PublishToMavenRepository>().configureEach {
        doFirst {
            val isDevVersion = publication.version.contains("-dev.")
            if (repository.name == "Repsy" && isDevVersion) {
                throw GradleException(
                    "Repsy releases must not carry a -dev.N suffix (got ${publication.version}). " +
                        "Drop the -PdevBuildNumber property."
                )
            }
            if (repository.name == "GitLab" && !isDevVersion) {
                throw GradleException(
                    "GitLab internal builds must be versioned Major.Minor.Patch-dev.N (got ${publication.version}). " +
                        "Pass -PdevBuildNumber=N."
                )
            }
        }
    }
}
