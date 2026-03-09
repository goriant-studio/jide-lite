package com.jidelite

import android.widget.EditText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import com.jidelite.ui.main.MainUiState
import com.jidelite.ui.main.MainViewModel

@RunWith(AndroidJUnit4::class)
class MainActivityIntegrationTest {

    companion object {
        private const val SAMPLE_MAIN_PATH = "src/main/java/demo/Main.java"
        private const val SAMPLE_MAIN_FILE_NAME = "Main.java"
        private const val NEW_FILE_PATH = "src/main/java/Main2.java"
        private const val NEW_FILE_NAME = "Main2.java"
    }

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        clearWorkspace()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun newActionCreatesAndOpensNextJavaFile() {
        launchActivity()

        composeRule.onNodeWithTag("explorer-new").assertExists()
        composeRule.onNodeWithTag("explorer-new").performClick()
        composeRule.onNodeWithText("File").assertExists()
        composeRule.onNodeWithText("File").performClick()

        composeRule.waitUntil(5_000) {
            currentUiState().selectedFilePath
                ?.replace(File.separatorChar, '/')
                ?.endsWith(NEW_FILE_PATH) == true &&
                    currentUiState().editorText.contains("Hello from Main2")
        }
        composeRule.onNodeWithText(NEW_FILE_NAME).assertExists()
        onView(isAssignableFrom(EditText::class.java))
            .check(matches(withText(containsString("Hello from Main2"))))
    }

    @Test
    fun formatActionReformatsCurrentEditorSource() {
        launchActivity()
        openSampleJavaFile()

        onView(isAssignableFrom(EditText::class.java))
            .perform(
                replaceText("public class Main{public static void main(String[] args){System.out.println(\"Hi\");}}"),
                closeSoftKeyboard()
            )
        composeRule.waitUntil(5_000) {
            currentUiState().editorText.startsWith("public class Main{") && currentUiState().isDirty
        }

        composeRule.onNodeWithTag("topbar-format").assertExists()
        composeRule.onNodeWithTag("topbar-format").performClick()

        val expected = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hi\");\n" +
                "    }\n" +
                "}"

        composeRule.waitUntil(5_000) {
            currentUiState().selectedFilePath
                ?.replace(File.separatorChar, '/')
                ?.endsWith(SAMPLE_MAIN_PATH) == true &&
                    currentUiState().editorText == expected &&
                    currentUiState().isDirty
        }

        onView(isAssignableFrom(EditText::class.java)).check(
            matches(
                withText(expected)
            )
        )
    }

    private fun launchActivity() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitUntil(10_000) {
            val uiState = currentUiState()
            uiState.workspacePath.isNotBlank() &&
                    uiState.files.any { it.name == "pom.xml" } &&
                    uiState.files.any { it.name == "Main.java" } &&
                    uiState.selectedFileName == "pom.xml" &&
                    uiState.editorText.contains("<project")
        }
    }

    private fun openSampleJavaFile() {
        composeRule.onNodeWithText(SAMPLE_MAIN_FILE_NAME).assertExists()
        composeRule.onNodeWithText(SAMPLE_MAIN_FILE_NAME).performClick()
        composeRule.waitUntil(5_000) {
            currentUiState().selectedFilePath
                ?.replace(File.separatorChar, '/')
                ?.endsWith(SAMPLE_MAIN_PATH) == true &&
                    currentUiState().editorText.contains("package demo;")
        }
    }

    private fun clearWorkspace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        deleteRecursively(context.filesDir.resolve("workspace"))
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) {
            return
        }

        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }

        file.delete()
    }

    private fun currentUiState(): MainUiState {
        var uiState: MainUiState? = null
        scenario?.onActivity { activity ->
            uiState = ViewModelProvider(activity)[MainViewModel::class.java].uiState
        }
        return checkNotNull(uiState)
    }
}
