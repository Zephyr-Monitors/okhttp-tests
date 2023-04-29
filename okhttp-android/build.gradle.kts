@file:Suppress("UnstableApiUsage")

import com.vanniktech.maven.publish.JavadocJar

plugins {
  id("com.android.library")
  kotlin("android")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("binary-compatibility-validator")
}

android {
  compileSdkPreview = "UpsideDownCake"

  defaultConfig {
    minSdk = 21

    // Make sure to use the AndroidJUnitRunner (or a sub-class) in order to hook in the JUnit 5 Test Builder
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    testInstrumentationRunnerArguments += mapOf(
      "runnerBuilder" to "de.mannodermaus.junit5.AndroidJUnit5Builder",
      "notPackage" to "org.bouncycastle"
    )

    buildFeatures {
      buildConfig = true
    }
  }

  compileOptions {
    targetCompatibility(JavaVersion.VERSION_11)
    sourceCompatibility(JavaVersion.VERSION_11)
  }

  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_11.toString()
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

dependencies {
  api(libs.squareup.okio)
  api(projects.okhttp)
  compileOnly(libs.androidx.annotation)
  compileOnly(libs.findbugs.jsr305)
  debugImplementation(libs.androidx.annotation)
  debugImplementation(libs.findbugs.jsr305)
  compileOnly(libs.animalsniffer.annotations)
  compileOnly(libs.robolectric.android)
  implementation(libs.kotlinx.coroutines.core)
  api(projects.loggingInterceptor)
  api(projects.okhttpBrotli)

  implementation(libs.androidx.tracing.ktx)
  implementation("com.google.net.cronet:cronet-okhttp:0.1.0")

  testImplementation("com.google.android.gms:play-services-cronet:18.0.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")
  testImplementation(projects.okhttpTestingSupport)
  testImplementation(libs.junit)
  testImplementation(libs.assertj.core)
  testImplementation(projects.okhttpTls)
  testImplementation(libs.androidx.test.runner)
  testImplementation("org.robolectric:robolectric:4.10")
  testImplementation("androidx.test.ext:junit-ktx:1.1.5")
  testImplementation("androidx.test.espresso:espresso-core:3.5.1")

  androidTestImplementation(projects.okhttpTls)
  androidTestImplementation(libs.assertj.core)
  androidTestImplementation(projects.mockwebserver3Junit5)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.junit.jupiter.api)
  androidTestImplementation(libs.junit5android.core)
  androidTestRuntimeOnly(libs.junit5android.runner)

  androidTestImplementation("com.google.android.gms:play-services-cronet:18.0.1")
  androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")
}

mavenPublishing {
  // AGP 7.2 embeds Dokka 4, which breaks publishing. Android modules are hardcoded to generate Javadoc instead of Gfm.
  configure(com.vanniktech.maven.publish.AndroidSingleVariantLibrary(publishJavadocJar=false))
}

// REMOVe
configurations.all {
  resolutionStrategy {
    force("androidx.annotation:annotation:1.6.0")
  }
}
