import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import java.net.URL
import nl.littlerobots.vcu.plugin.versionCatalogUpdate
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension

buildscript {
  dependencies {
    classpath(libs.gradleplugin.dokka)
    classpath(libs.gradleplugin.kotlin)
    classpath(libs.gradleplugin.android.junit5)
    classpath(libs.gradleplugin.android)
    classpath(libs.gradleplugin.palantir.graal)
    classpath(libs.gradleplugin.aqute.bnd)
    classpath(libs.gradleplugin.johnrengelman.shadow)
    classpath(libs.gradleplugin.japicmp)
    classpath(libs.gradleplugin.animalsniffer)
    classpath(libs.gradleplugin.errorprone)
    classpath(libs.gradleplugin.spotless)
    classpath(libs.gradleplugin.vanniktech)
    classpath(libs.gradleplugin.benmanes.versions)
    classpath(libs.gradleplugin.littlerobots.vcu)
  }

  repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
  }
}

apply(plugin = "com.vanniktech.maven.publish.base")
apply(plugin = "com.github.ben-manes.versions")
apply(plugin = "nl.littlerobots.version-catalog-update")

allprojects {
  group = "com.squareup.okhttp3"
  version = "5.0.0-SNAPSHOT"

  repositories {
    mavenCentral()
    google()
  }

  tasks.create("downloadDependencies") {
    description = "Download all dependencies to the Gradle cache"
    doLast {
      for (configuration in configurations) {
        if (configuration.isCanBeResolved) {
          configuration.files
        }
      }
    }
  }

  normalization {
    runtimeClasspath {
      metaInf {
        ignoreAttribute("Bnd-LastModified")
      }
    }
  }
}

/** Configure building for Java+Kotlin projects. */
subprojects {
  val project = this@subprojects
  if (project.name == "android-test") return@subprojects
  if (project.name == "okhttp-bom") return@subprojects
  if (project.name == "regression-test") return@subprojects

  apply(plugin = "checkstyle")
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "ru.vyarus.animalsniffer")
  apply(plugin = "org.jetbrains.dokka")
  apply(plugin = "biz.aQute.bnd.builder")

  tasks.withType<JavaCompile> {
    options.encoding = Charsets.UTF_8.toString()
  }

  configure<JavaPluginExtension> {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(11))
    }
  }

  tasks.withType<Checkstyle>().configureEach {
    exclude("**/CipherSuite.java")
  }

  val checkstyleConfig: Configuration by configurations.creating
  dependencies {
    checkstyleConfig("com.puppycrawl.tools:checkstyle:9.2") {
      isTransitive = false
    }
  }

  afterEvaluate {
    configure<CheckstyleExtension> {
      config = resources.text.fromArchiveEntry(checkstyleConfig, "google_checks.xml")
      toolVersion = libs.versions.checkStyle.get()
      sourceSets = listOf(project.sourceSets["main"])
    }

    // Animal Sniffer confirms we generally don't use APIs not on Java 8.
    configure<AnimalSnifferExtension> {
      annotation = "okhttp3.internal.SuppressSignatureCheck"
      sourceSets = listOf(project.sourceSets["main"])
    }

    val signature: Configuration by configurations.getting
    dependencies {
      signature(libs.signature.android.apilevel21)
      signature(libs.codehaus.signature.java18)
    }
  }

  tasks.withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_1_8.toString()
      freeCompilerArgs = listOf(
        "-Xjvm-default=compatibility",
        "-Xopt-in=kotlin.RequiresOptIn"
      )
    }
  }

  val platform = System.getProperty("okhttp.platform", "jdk9")
  val testJavaVersion = System.getProperty("test.java.version", "11").toInt()

  val testRuntimeOnly: Configuration by configurations.getting

  afterEvaluate {
    dependencies {
      testRuntimeOnly(libs.junit.jupiter.engine)
      testRuntimeOnly(libs.junit.vintage.engine)
    }
  }

  tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs = jvmArgs!! + listOf(
      "-Dokhttp.platform=$platform",
      "-XX:+HeapDumpOnOutOfMemoryError"
    )

    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    javaLauncher.set(javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(testJavaVersion))
    })

    maxParallelForks = Runtime.getRuntime().availableProcessors() * 2
    testLogging {
      exceptionFormat = TestExceptionFormat.FULL
    }

    systemProperty("okhttp.platform", platform)
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
  }

  if (platform == "jdk8alpn") {
    // Add alpn-boot on Java 8 so we can use HTTP/2 without a stable API.
    val alpnBootVersion = alpnBootVersion()
    if (alpnBootVersion != null) {
      val alpnBootJar = configurations.detachedConfiguration(
        dependencies.create("org.mortbay.jetty.alpn:alpn-boot:$alpnBootVersion")
      ).singleFile
      tasks.withType<Test> {
        jvmArgs = jvmArgs!! + listOf("-Xbootclasspath/p:${alpnBootJar}")
      }
    }
  } else if (platform == "conscrypt") {
    dependencies {
      testRuntimeOnly(libs.conscrypt)
    }
  } else if (platform == "openjsse") {
    dependencies {
      testRuntimeOnly(libs.openjsse)
    }
  }

  tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
  }
}

/** Configure publishing and signing for published Java and JavaPlatform subprojects. */
subprojects {
  tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets.configureEach {
      reportUndocumented.set(false)
      skipDeprecated.set(true)
      jdkVersion.set(8)
      perPackageOption {
        matchingRegex.set("okhttp3\\.internal.*")
        suppress.set(true)
      }
      perPackageOption {
        matchingRegex.set("mockwebserver3\\.internal.*")
        suppress.set(true)
      }
      if (project.file("Module.md").exists()) {
        includes.from(project.file("Module.md"))
      }
      externalDocumentationLink {
        url.set(URL("https://square.github.io/okio/2.x/okio/"))
        packageListUrl.set(URL("https://square.github.io/okio/2.x/okio/package-list"))
      }
    }
    if (name == "dokkaGfm") {
      outputDirectory.set(file("${rootDir}/docs/4.x"))
    }
  }

  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(SonatypeHost.S01)
      signAllPublications()
      pom {
        name.set(project.name)
        description.set("Square’s meticulous HTTP client for Java and Kotlin.")
        url.set("https://square.github.io/okhttp/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        scm {
          connection.set("scm:git:https://github.com/square/okhttp.git")
          developerConnection.set("scm:git:ssh://git@github.com/square/okhttp.git")
          url.set("https://github.com/square/okhttp")
        }
        developers {
          developer {
            name.set("Square, Inc.")
          }
        }
      }
    }
  }
}

tasks.wrapper {
  distributionType = Wrapper.DistributionType.ALL
}

versionCatalogUpdate {
  sortByKey = true

  keep {
    // gradle profiles are used to enable/disable some modules so keep all
    keepUnusedVersions.set(true)
    keepUnusedLibraries.set(true)
    keepUnusedPlugins.set(true)
  }
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
  rejectVersionIf {
    candidate.run {
      version.contains("alpha") ||
        version.contains("beta") ||
        version.contains("1.6.20-M")
    }
  }
}
