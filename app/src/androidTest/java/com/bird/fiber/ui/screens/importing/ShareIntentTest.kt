package com.bird.fiber.ui.screens.importing

import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.bird.fiber.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** 使用实际 MainActivity、ContentResolver 和系统 Intent 分发，覆盖后台热启动入口。 */
class ShareIntentTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun sharingMarkdownResumesStoppedActivityAndDialogRemainsDismissibleAcrossRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val name = "fiber-share-regression-${System.nanoTime()}.md"
        val uri = requireNotNull(resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/markdown")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/FiberImportRegression")
            }
        ))
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/markdown"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        var activity = instrumentation.startActivitySync(launchIntent) as MainActivity

        fun resumedActivity(): MainActivity? {
            var resumed: MainActivity? = null
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
            }
            return resumed
        }

        fun recreateActivity() {
            val previous = activity
            instrumentation.runOnMainSync { previous.recreate() }
            compose.waitUntil(10_000) {
                resumedActivity()?.let { it !== previous } == true
            }
            activity = requireNotNull(resumedActivity())
        }

        try {
            requireNotNull(resolver.openOutputStream(uri)).use {
                it.write("# 后台分享回归\n\n从后台恢复后可以取消导入。".toByteArray())
            }
            repeat(3) { index ->
                instrumentation.runOnMainSync { activity.moveTaskToBack(true) }
                compose.waitUntil(10_000) { activity.lifecycle.currentState == Lifecycle.State.CREATED }
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "text/markdown"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(name, uri)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                compose.waitUntil(10_000) {
                    compose.onAllNodes(androidx.compose.ui.test.hasText(name))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("保存到库").assertIsDisplayed()
                compose.runOnIdle {
                    assertEquals(Intent.ACTION_SEND, activity.intent.action)
                    assertFalse(activity.intent.hasExtra(Intent.EXTRA_STREAM))
                }
                if (index == 1) {
                    recreateActivity()
                    compose.onNodeWithText(name).assertIsDisplayed()
                }
                compose.onNodeWithText("取消").performClick()
                compose.onNodeWithText("保存到库").assertDoesNotExist()
                compose.runOnIdle {
                    assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
                }
            }
            recreateActivity()
            compose.onNodeWithText("保存到库").assertDoesNotExist()
        } finally {
            resolver.delete(uri, null, null)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
