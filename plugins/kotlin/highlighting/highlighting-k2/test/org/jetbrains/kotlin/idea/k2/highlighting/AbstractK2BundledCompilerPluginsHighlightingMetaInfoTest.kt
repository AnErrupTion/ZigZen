// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.jetbrains.kotlin.idea.base.test.ensureFilesResolved
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * N.B. These test rely on the fake compiler plugins jars present in the testdata (`*_fake_plugin.jar` files).
 * Those plugins do not contain anything besides
 * 'META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar' files with corresponding
 * plugin's registrar qualified name in it, and are needed to make `KotlinK2BundledCompilerPlugins` work properly.
 */
abstract class AbstractK2BundledCompilerPluginsHighlightingMetaInfoTest : AbstractK2HighlightingMetaInfoTest() {
    override fun highlightingFileNameSuffix(testKtFile: File): String = HIGHLIGHTING_EXTENSION

    /**
     * Test cases reference fake compiler plugins' jars which lay in the test data directory. This directory is located differently
     * in local and CI (TeamCity) environments.
     * To overcome this, we use this path macro in test cases, and it is expected to be correctly substituted
     * by [org.jetbrains.kotlin.idea.fir.extensions.KtCompilerPluginsProviderIdeImpl].
     */
    private val testDirPlaceholder: String = "TEST_DIR"

    override fun setUp() {
        super.setUp()

        // N.B. We don't use PathMacroContributor here because it's too late to register at this point
        PathMacros.getInstance().setMacro(testDirPlaceholder, testDataDirectory.toString())
    }

    override fun tearDown() {
        runAll(
            { PathMacros.getInstance().setMacro(testDirPlaceholder, null) },
            { super.tearDown() },
        )
    }

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val file = files.first() as KtFile

        withCustomCompilerOptions(file.text, project, module) {
            ensureFilesResolved(file)
            super.doMultiFileTest(files, globalDirectives)
        }
    }

    override fun getDefaultProjectDescriptor(): ProjectDescriptorWithStdlibSources {
        return object : ProjectDescriptorWithStdlibSources() {
            override fun configureModule(module: Module, model: ModifiableRootModel) {
                super.configureModule(module, model)

                // annotations for lombok plugin
                MavenDependencyUtil.addFromMaven(model, LOMBOK_MAVEN_COORDINATES)

                // annotations for serialization plugin
                MavenDependencyUtil.addFromMaven(model, KOTLINX_SERIALIZATION_CORE_MAVEN_COORDINATES)

                // annotations for parcelize plugin
                MavenDependencyUtil.addFromMaven(model, PARCELIZE_RUNTIME_MAVEN_COORDINATES)
            }
        }
    }
}

private const val LOMBOK_MAVEN_COORDINATES = "org.projectlombok:lombok:1.18.26"

private const val KOTLINX_SERIALIZATION_CORE_MAVEN_COORDINATES = "org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.0"

private const val PARCELIZE_RUNTIME_MAVEN_COORDINATES = "org.jetbrains.kotlin:kotlin-parcelize-runtime:1.8.20"
