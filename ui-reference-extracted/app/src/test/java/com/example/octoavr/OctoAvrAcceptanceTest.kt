package com.example.octoavr

import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.octoavr.build.BuildPipeline
import com.example.octoavr.compiler.CompilationEngine
import com.example.octoavr.compiler.HexAndElfParser
import com.example.octoavr.files.FirmwareExportManager
import com.example.octoavr.model.BuildAction
import com.example.octoavr.model.BuildState
import com.example.octoavr.model.DiagnosticSeverity
import com.example.octoavr.project.Atmega32aExamples
import com.example.octoavr.project.ProjectRepository
import com.example.octoavr.toolchain.AvrToolchainManager
import com.example.octoavr.ui.OctoIdeScreen
import com.example.octoavr.ui.OctoIdeViewModel
import com.example.ui.theme.OctoAVR32ATheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OctoAvrAcceptanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var projectRepo: ProjectRepository
    private lateinit var toolchainManager: AvrToolchainManager
    private lateinit var pipeline: BuildPipeline
    private lateinit var exportManager: FirmwareExportManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        projectRepo = ProjectRepository(context)
        toolchainManager = AvrToolchainManager(context)
        pipeline = BuildPipeline(toolchainManager, CompilationEngine())
        exportManager = FirmwareExportManager(context)
    }

    @Test
    fun acceptanceTests_1_to_6_and_8_blinkElfHexValidateExportZFlasher() = runBlocking {
        // Test 1 & Test 8: Create an ATmega32A project using avr/io.h and util/delay.h and compile LED blink
        val blinkTemplate = Atmega32aExamples.all.first { it.id == "led_blink" }
        val project = projectRepo.createProjectFromTemplate("ATmega32A_Blink_Test", blinkTemplate)

        val result = pipeline.execute(
            project = project,
            action = BuildAction.BUILD,
            onStageChanged = {},
            onLog = { _, _, _ -> }
        )

        assertTrue("Expected BuildState.Success but got $result", result is BuildState.Success)
        val success = result as BuildState.Success

        // Test 2: Valid ELF file (EM_AVR = 83)
        assertNotNull("ELF path must not be null", success.elfPath)
        val elfFile = File(success.elfPath!!)
        assertTrue("ELF file must exist", elfFile.exists())
        assertTrue("ELF must pass EM_AVR validation", HexAndElfParser.isValidAvrElf(elfFile))
        assertNotNull("Memory usage must be measured", success.memoryUsage)
        assertTrue("Flash bytes must be > 0", success.memoryUsage!!.flashBytes > 0)

        // Test 3 & Test 4: Valid Intel HEX file with checksummed records + EOF
        assertNotNull("HEX path must not be null", success.hexPath)
        val hexFile = File(success.hexPath!!)
        assertTrue("HEX file must exist", hexFile.exists())
        val hexValidation = HexAndElfParser.validateIntelHex(hexFile)
        assertTrue("HEX validation failed: ${hexValidation.errorReason}", hexValidation.isValid)
        assertTrue("HEX must contain data bytes", hexValidation.dataByteCount > 0)
        assertTrue("HEX must end with EOF record", hexValidation.hasEofRecord)

        // Test 5: Export HEX file to Downloads-equivalent SAF URI
        val downloadsDir = File(context.cacheDir, "simulated_downloads").apply { mkdirs() }
        val exportedHex = File(downloadsDir, "ATmega32A_Blink.hex")
        val exportResult = exportManager.copyFileToUri(hexFile, Uri.fromFile(exportedHex))
        assertTrue("HEX export should succeed", exportResult.isSuccess)
        assertEquals(hexFile.readText(), exportedHex.readText())

        // Test 6: Open / share HEX with ZFlasher AVR
        val shadowPm = Shadows.shadowOf(context.packageManager)
        val zflasherInfo = PackageInfo().apply {
            packageName = FirmwareExportManager.ZFLASHER_PACKAGE
        }
        shadowPm.installPackage(zflasherInfo)
        assertTrue(exportManager.isZFlasherInstalled())
        val launchRes = exportManager.launchWithZFlasherOrChooser(hexFile)
        assertTrue(
            "Launch outcome should target ZFlasher or chooser, got ${launchRes.outcome}: ${launchRes.message}",
            launchRes.outcome != FirmwareExportManager.LaunchOutcome.FAILED
        )
    }

    @Test
    fun acceptanceTest_7_syntaxErrorProducesRealCompilerDiagnostic() = runBlocking {
        val project = projectRepo.createBlankProject("Broken_Syntax_Test")
        val broken = project.copy(
            files = project.files.map {
                it.copy(
                    content = """
                        #include <avr/io.h>
                        int main(void) {
                            DDRB = 0xFF
                            undeclared_register = 1;
                            return 0;
                        }
                    """.trimIndent()
                )
            }
        )
        projectRepo.saveProject(broken)

        val result = pipeline.execute(
            project = broken,
            action = BuildAction.VERIFY,
            onStageChanged = {},
            onLog = { _, _, _ -> }
        )

        assertTrue("Expected BuildState.Failure for syntax error, got $result", result is BuildState.Failure)
        val failure = result as BuildState.Failure
        assertTrue(
            "Compiler must emit at least one ERROR diagnostic",
            failure.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
        )
    }

    @Test
    fun acceptanceTest_9_multiFileHalProjectBuildsCleanly() = runBlocking {
        val multiFileTemplate = Atmega32aExamples.all.first { it.id == "multifile_hal" }
        val project = projectRepo.createProjectFromTemplate("ATmega32A_MultiFile_Test", multiFileTemplate)
        assertEquals(3, project.files.size)

        val result = pipeline.execute(
            project = project,
            action = BuildAction.BUILD,
            onStageChanged = {},
            onLog = { _, _, _ -> }
        )

        assertTrue("Expected multi-file BuildState.Success but got $result", result is BuildState.Success)
        val success = result as BuildState.Success
        val hexValidation = HexAndElfParser.validateIntelHex(File(success.hexPath!!))
        assertTrue(hexValidation.isValid)
        assertTrue(hexValidation.dataByteCount > 0)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun acceptanceTest_10a_phoneLayoutRendersEditorAndToolbar() {
        val vm = OctoIdeViewModel(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            OctoAVR32ATheme {
                OctoIdeScreen(viewModel = vm)
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("code_editor_textfield"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_verify_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toolbar_build_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("code_editor_textfield").assertIsDisplayed()
        composeTestRule.onNodeWithTag("console_header").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1024dp-h768dp")
    fun acceptanceTest_10b_tabletLayoutRendersDockedExplorerAndEditor() {
        val vm = OctoIdeViewModel(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            OctoAVR32ATheme {
                OctoIdeScreen(viewModel = vm)
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("new_project_btn"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("new_project_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("code_editor_textfield").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toolbar_build_btn").assertIsDisplayed()
    }

    @Test
    fun acceptanceTest_all10Atmega32aExamplesCompileToValidElfAndHex() = runBlocking {
        assertEquals(10, Atmega32aExamples.all.size)
        for (example in Atmega32aExamples.all) {
            val proj = projectRepo.createProjectFromTemplate("Ex_${example.id}", example)
            val result = pipeline.execute(
                project = proj,
                action = BuildAction.BUILD,
                onStageChanged = {},
                onLog = { _, _, _ -> }
            )
            assertTrue("Example ${example.id} failed: $result", result is BuildState.Success)
            val ok = result as BuildState.Success
            assertTrue("Example ${example.id} ELF invalid", HexAndElfParser.isValidAvrElf(File(ok.elfPath!!)))
            assertTrue("Example ${example.id} HEX invalid", HexAndElfParser.validateIntelHex(File(ok.hexPath!!)).isValid)
        }
    }

    @Test
    fun acceptanceTest_sysrootExtractionIncludesDeviceSpecsAndMultilibCrt() = runBlocking {
        val status = toolchainManager.ensureToolchainReady()
        val sysroot = toolchainManager.sysrootDir
        assertTrue(
            "Extracted sysroot must include avr/include/avr/io.h",
            File(sysroot, "avr/include/avr/io.h").exists()
        )
        assertTrue(
            "Extracted sysroot must include device-specs/specs-atmega32a",
            File(sysroot, "lib/gcc/avr/${AvrToolchainManager.GCC_VERSION}/device-specs/specs-atmega32a").exists()
        )
        assertTrue(
            "Extracted sysroot must include avr5/crtatmega32a.o",
            File(sysroot, "avr/lib/avr5/crtatmega32a.o").exists()
        )
        assertTrue("Host or native toolchain should be ready in test: $status", status.isReady)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun acceptanceTest_referenceAndInspectorDialogsWork() {
        val vm = OctoIdeViewModel(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            OctoAVR32ATheme {
                OctoIdeScreen(viewModel = vm)
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("toolbar_more_btn"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolbar_more_btn").performClick()
        composeTestRule.onNodeWithTag("menu_atmega32a_ref").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("ATmega32A Reference")).assertIsDisplayed()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Calc")).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("USART Baud Calculator")).assertIsDisplayed()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Close")).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("toolbar_more_btn").performClick()
        composeTestRule.onNodeWithTag("menu_firmware_inspector").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Firmware Inspector")).assertIsDisplayed()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Close")).performClick()
    }
}
