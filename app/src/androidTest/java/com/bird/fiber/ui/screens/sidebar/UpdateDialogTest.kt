package com.bird.fiber.ui.screens.sidebar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.bird.fiber.data.update.AppUpdateInfo
import com.bird.fiber.ui.theme.FiberTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdateDialogTest {
    @get:Rule val compose = createComposeRule()

    private val update = AppUpdateInfo(
        versionName = "9.9.9",
        releaseNotes = "更新说明",
        downloadUrl = "https://example.com/Fiber.apk",
        releasePageUrl = "https://example.com/releases/9.9.9"
    )

    @Test
    fun apkDownloadIsHandedOffBeforeDialogDismisses() {
        val events = mutableListOf<String>()
        compose.setContent {
            FiberTheme(dynamicColor = false) {
                UpdateDialog(
                    update = update,
                    onDismiss = { events += "dismiss" },
                    onDownload = {
                        assertEquals(update, it)
                        events += "download"
                        1L
                    }
                )
            }
        }

        compose.onNodeWithText("下载").performClick()
        compose.runOnIdle { assertEquals(listOf("download", "dismiss"), events) }
    }

    @Test
    fun pendingDownloadDisablesDuplicateRequestsAndCancellation() {
        val queued = CompletableDeferred<Long>()
        var requests = 0
        var dismissals = 0
        compose.setContent {
            FiberTheme(dynamicColor = false) {
                UpdateDialog(update, { dismissals++ }) {
                    requests++
                    queued.await()
                }
            }
        }

        compose.onNodeWithText("下载").performClick()
        compose.onNodeWithText("正在处理…").assertIsNotEnabled().performClick()
        compose.onNodeWithText("取消").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, requests)
            assertEquals(0, dismissals)
            queued.complete(1L)
        }
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun failedEnqueueKeepsDialogOpenAndAllowsRetry() {
        var requests = 0
        var dismissals = 0
        compose.setContent {
            FiberTheme(dynamicColor = false) {
                UpdateDialog(update, { dismissals++ }) {
                    requests++
                    if (requests == 1) error("系统下载管理器不可用")
                    1L
                }
            }
        }

        compose.onNodeWithText("下载").performClick()
        compose.onNodeWithText("无法开始下载，请稍后重试").assertIsDisplayed()
        compose.onNodeWithText("下载").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, dismissals) }

        compose.onNodeWithText("下载").performClick()
        compose.runOnIdle {
            assertEquals(2, requests)
            assertEquals(1, dismissals)
        }
    }

    @Test
    fun missingApkClearlyLabelsReleasePageFallback() {
        compose.setContent {
            FiberTheme(dynamicColor = false) {
                UpdateDialog(update.copy(downloadUrl = null), {}, { error("不应启动下载") })
            }
        }

        compose.onNodeWithText("查看发布页").assertIsDisplayed()
        compose.onNodeWithText("下载").assertDoesNotExist()
    }

    @Test
    fun longReleaseNotesScrollWithoutHidingButtonsOrDownloadError() {
        val longNotes = (1..100).joinToString("\n") { "第 $it 项更新说明" }
        compose.setContent {
            FiberTheme(dynamicColor = false) {
                UpdateDialog(update.copy(releaseNotes = longNotes), {}, { error("下载失败") })
            }
        }

        compose.onNodeWithText("下载").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.onNodeWithText("下载").assertIsDisplayed().performClick()
        compose.onNodeWithText("无法开始下载，请稍后重试").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
    }
}
