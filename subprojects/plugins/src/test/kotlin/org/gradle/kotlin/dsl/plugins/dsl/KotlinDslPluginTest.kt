package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.kotlin.dsl.fixtures.customDaemonRegistry
import org.gradle.kotlin.dsl.fixtures.customInstallation
import org.gradle.kotlin.dsl.fixtures.AbstractPluginTest

import org.gradle.testkit.runner.TaskOutcome

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertThat
import org.junit.Test

import java.io.File


class KotlinDslPluginTest : AbstractPluginTest() {

    @Test
    fun `gradle kotlin dsl api dependency is added`() {

        withKotlinDevPluginRepository()

        withBuildScript("""

            plugins {
                `kotlin-dsl`
            }

            repositories {
                kotlinDev()
            }

        """)

        withFile("src/main/kotlin/code.kt", """

            // src/main/kotlin
            import org.gradle.kotlin.dsl.GradleDsl

            // src/generated
            import org.gradle.kotlin.dsl.embeddedKotlinVersion

        """)

        val result = buildWithPlugin("classes")

        assertThat(result.outcomeOf(":compileKotlin"), equalTo(TaskOutcome.SUCCESS))
    }

    private
    fun withKotlinDevPluginRepository() {
        withSettings(pluginManagementBlockWithKotlinDevRepository)
    }

    @Test
    fun `gradle kotlin dsl api is available for test implementation`() {

        withKotlinDevPluginRepository()

        withBuildScript("""

            plugins {
                `java-gradle-plugin`
                `kotlin-dsl`
            }

            repositories {
                kotlinDev()
                jcenter()
            }

            dependencies {
                testCompile("junit:junit:4.12")
            }

        """)

        withFile("src/main/kotlin/code.kt", """

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.kotlin.dsl.embeddedKotlinVersion

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.run {
                        println("Plugin Using Embedded Kotlin " + embeddedKotlinVersion)
                    }
                }
            }
        """)

        withFile("src/test/kotlin/test.kt", """

            import org.gradle.testfixtures.ProjectBuilder
            import org.junit.Test
            import org.gradle.kotlin.dsl.*

            class MyTest {

                @Test
                fun `my test`() {
                    ProjectBuilder.builder().build().run {
                        apply<MyPlugin>()
                    }
                }
            }
        """)

        assertThat(
            outputOf("test", "-i"),
            containsString("Plugin Using Embedded Kotlin "))
    }

    @Test
    fun `gradle kotlin dsl api is available in test-kit injected plugin classpath`() {

        withKotlinDevPluginRepository()

        withBuildScript("""

            plugins {
                `java-gradle-plugin`
                `kotlin-dsl`
            }

            repositories {
                kotlinDev()
                jcenter()
            }

            dependencies {
                testCompile("junit:junit:4.12")
                testCompile(gradleTestKit())
            }

            gradlePlugin {
                (plugins) {
                    "myPlugin" {
                        id = "my-plugin"
                        implementationClass = "my.MyPlugin"
                    }
                }
            }

        """)

        withFile("src/main/kotlin/my/code.kt", """
            package my

            import org.gradle.api.*
            import org.gradle.kotlin.dsl.*

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    println("Plugin Using Embedded Kotlin " + embeddedKotlinVersion)
                }
            }
        """)

        withFile("src/test/kotlin/test.kt", """

            import java.io.File

            import org.gradle.testkit.runner.GradleRunner

            import org.hamcrest.CoreMatchers.containsString
            import org.junit.Assert.assertThat

            import org.junit.Rule
            import org.junit.Test
            import org.junit.rules.TemporaryFolder

            class MyTest {

                @JvmField @Rule val temporaryFolder = TemporaryFolder()

                val projectRoot by lazy {
                    File(temporaryFolder.root, "test").apply { mkdirs() }
                }

                @Test
                fun `my test`() {
                    // given:
                    File(projectRoot, "build.gradle.kts")
                        .writeText("plugins { id(\"my-plugin\") }")

                    // and:
                    System.setProperty("org.gradle.daemon.idletimeout", "1000")
                    System.setProperty("org.gradle.daemon.registry.base", "${escapedPathOf(customDaemonRegistry())}")
                    File(projectRoot, "gradle.properties").writeText("org.gradle.jvmargs=-Xmx128m")

                    // and:
                    val runner = GradleRunner.create()
                        .withGradleInstallation(File("${escapedPathOf(customInstallation())}"))
                        .withProjectDir(projectRoot)
                        .withPluginClasspath()
                        .forwardOutput()

                    // when:
                    val result = runner.withArguments("help").build()

                    // then:
                    assertThat(result.output, containsString("Plugin Using Embedded Kotlin "))
                }
            }

        """)

        assertThat(
            outputOf("test", "-i"),
            containsString("Plugin Using Embedded Kotlin "))
    }

    @Test
    fun `sam-with-receiver kotlin compiler plugin is applied to production code`() {

        withKotlinDevPluginRepository()

        withBuildScript("""

            plugins {
                `kotlin-dsl`
            }

            repositories {
                kotlinDev()
            }
        """)

        withFile("src/main/kotlin/code.kt", """

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.run {
                        copy {
                            from("build.gradle.kts")
                            into("build/build.gradle.kts.copy")
                        }
                    }
                }
            }

        """)

        val result = buildWithPlugin("classes")

        assertThat(result.outcomeOf(":compileKotlin"), equalTo(TaskOutcome.SUCCESS))
    }

    private
    fun escapedPathOf(file: File) =
        file.absolutePath.replace("\\", "\\\\")

    private
    fun outputOf(vararg arguments: String) =
        buildWithPlugin(*arguments).output
}
