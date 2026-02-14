plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
	id("org.jetbrains.kotlin.plugin.compose")
}

android {
	namespace = "com.rotator.server"
	compileSdk = 34

	defaultConfig {
		applicationId = "com.rotator.server"
		minSdk = 24
		targetSdk = 34
		versionCode = 1
		versionName = "1.0"
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
		jvmTarget = "17"
	}

	buildFeatures {
		compose = true
	}
}

dependencies {
	implementation("androidx.core:core-ktx:1.12.0")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
	implementation("androidx.activity:activity-compose:1.8.2")

	// Jetpack Compose
	implementation(platform("androidx.compose:compose-bom:2023.10.01"))
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.ui:ui-graphics")
	implementation("androidx.compose.ui:ui-tooling-preview")
	implementation("androidx.compose.material3:material3")

	// USB Serial
	implementation("com.github.mik3y:usb-serial-for-android:3.7.3")

	// Coroutines
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

	// Lifecycle
	implementation("androidx.lifecycle:lifecycle-service:2.7.0")
}
