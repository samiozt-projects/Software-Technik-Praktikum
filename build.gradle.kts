import korlibs.korge.gradle.*
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
	alias(libs.plugins.korge)
    kotlin("plugin.serialization") version "2.0.0"
    jacoco
}

korge {
	id = "com.sample.demo"
	targetJvm()
	serializationJson()
}

dependencies {
    add("commonMainApi", project(":deps"))
    add("jvmMainImplementation", "org.bytedeco:javacv-platform:1.5.9")
}

kotlin {
    sourceSets {
        commonMain {
            resources.srcDir("res")
        }
    }
}

/** JVM test task whose execution data is used by the Jacoco coverage report. */
val jvmTestTask = tasks.named("jvmTest") {
    (this as Test).maxHeapSize = "2g"
}

/** Hand-written production classes included in reports and verification. */
val coverageClassDirectories = files(jacocoClassTree("main"))

/** Generates the Jacoco XML report uploaded by the Teamscale workflow. */
val jacocoTestReport by tasks.registering(JacocoReport::class) {
    dependsOn(jvmTestTask)

    executionData(layout.buildDirectory.file("jacoco/jvmTest.exec"))
    sourceDirectories.setFrom(files("src"))
    classDirectories.setFrom(coverageClassDirectories)

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoTestReport/jacocoTestReport.xml"))
        csv.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoTestReport/html"))
    }
}

/**
 * Guards the current coverage foundation for central production packages.
 *
 * The limits deliberately leave room for normal development while preventing a substantial,
 * unnoticed regression. They can be raised as more targeted tests are added.
 */
val jacocoCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    dependsOn(jvmTestTask)
    executionData(layout.buildDirectory.file("jacoco/jvmTest.exec"))
    sourceDirectories.setFrom(files("src"))
    classDirectories.setFrom(coverageClassDirectories)

    violationRules {
        mapOf(
            "domain" to "0.90",
            "simulation" to "0.90",
            "persistence" to "0.80",
            "network" to "0.70",
            "scenes" to "0.55"
        ).forEach { (packageName, minimumCoverage) ->
            rule {
                element = "PACKAGE"
                includes = listOf(packageName)
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = minimumCoverage.toBigDecimal()
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(jacocoCoverageVerification)
}

/**
 * Returns the compiled class tree for a JVM source set with generated Kotlin/KorGE artifacts
 * excluded, so Teamscale receives coverage for hand-written main and test code only.
 */
fun jacocoClassTree(sourceSetName: String) =
    fileTree(layout.buildDirectory.dir("classes/kotlin/jvm/$sourceSetName").get().asFile) {
        exclude(
            "**/KR.class",
            "**/__KR*.class",
            "**/Typed*.class",
            "**/*\$Companion.class",
            "**/*\$WhenMappings.class",
            "**/*\$serializer.class",
        )
    }

tasks.matching { it.name == "jvmTest" || it.name == "test" }.configureEach {
    finalizedBy(jacocoTestReport)
}
