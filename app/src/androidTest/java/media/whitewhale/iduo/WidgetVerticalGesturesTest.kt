package media.whitewhale.iduo

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetVerticalGesturesTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun hierarchy(content: View): FrameLayout {
        val root = FrameLayout(context)
        val host = AppWidgetHostView(context)
        host.addView(content, FrameLayout.LayoutParams(300, 300))
        root.addView(host, FrameLayout.LayoutParams(300, 300))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 300, 300)
        return root
    }

    private fun zeroPaddingHost(content: View): AppWidgetHostView {
        val host = ZeroPaddingWidgetHost(context, 2048)
        val create = ZeroPaddingWidgetHost::class.java.getDeclaredMethod(
            "onCreateView",
            android.content.Context::class.java,
            Int::class.javaPrimitiveType,
            AppWidgetProviderInfo::class.java,
        ).apply { isAccessible = true }
        return (create.invoke(host, context, 0, AppWidgetProviderInfo()) as AppWidgetHostView).apply {
            addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    @Test fun staticClockBodyAllowsHomeVerticalGesture() {
        val root = hierarchy(TextView(context).apply { text = "12:34" })
        assertFalse(nativeWidgetConsumesVerticalGesture(root, Offset(150f, 150f)))
    }

    @Test fun verticalScrollViewConsumesAtBothEndpoints() {
        val scroll = ScrollView(context).apply {
            addView(TextView(context).apply {
                text = List(100) { "Forecast $it" }.joinToString("\n")
            }, FrameLayout.LayoutParams(300, 1800))
        }
        val root = hierarchy(scroll)
        scroll.scrollTo(0, 0)
        assertTrue(nativeWidgetConsumesVerticalGesture(root, Offset(150f, 150f)))
        scroll.scrollTo(0, scroll.getChildAt(0).height - scroll.height)
        assertTrue(nativeWidgetConsumesVerticalGesture(root, Offset(150f, 150f)))
    }

    @Test fun horizontalOnlyContainerAllowsHomeVerticalGesture() {
        val horizontal = HorizontalScrollView(context).apply {
            addView(TextView(context).apply { text = "Hourly timeline" }, FrameLayout.LayoutParams(1200, 300))
        }
        val root = hierarchy(horizontal)
        assertFalse(nativeWidgetConsumesVerticalGesture(root, Offset(150f, 150f)))
    }

    @Test fun nativeWidgetListKeepsVerticalStreamFromScrollableHomeParent() {
        lateinit var providerList: ScrollView
        lateinit var homeScroll: androidx.compose.foundation.ScrollState
        compose.setContent {
            homeScroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(homeScroll)) {
                AndroidView(factory = { viewContext ->
                    providerList = ScrollView(viewContext).apply {
                        addView(TextView(viewContext).apply {
                            text = List(100) { "Calendar event $it" }.joinToString("\n")
                        }, FrameLayout.LayoutParams(300, 1800))
                    }
                    zeroPaddingHost(providerList)
                }, modifier = Modifier.height(300.dp).testTag("scrolling-widget"))
                Box(Modifier.height(1000.dp))
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("scrolling-widget").performTouchInput { swipeUp() }
        compose.waitForIdle()

        assertTrue("The provider's native list must receive the vertical drag", providerList.scrollY > 0)
        assertEquals("Home must not steal a drag that starts in provider list content", 0, homeScroll.value)
    }

    @Test fun nativeWidgetStillReceivesLongPress() {
        val longPressed = AtomicBoolean(false)
        compose.setContent {
            val viewContext = LocalContext.current
            AndroidView(factory = {
                val body = TextView(viewContext).apply {
                    text = List(100) { "Calendar event $it" }.joinToString("\n")
                    isLongClickable = true
                    setOnLongClickListener { longPressed.set(true); true }
                }
                val providerList = ScrollView(viewContext).apply {
                    addView(body, FrameLayout.LayoutParams(300, 1800))
                }
                zeroPaddingHost(providerList)
            }, modifier = Modifier.height(300.dp).testTag("long-press-widget"))
        }
        compose.onNodeWithTag("long-press-widget").performTouchInput { down(center) }
        android.os.SystemClock.sleep(650)
        compose.onNodeWithTag("long-press-widget").performTouchInput { up() }
        compose.waitForIdle()
        assertTrue("Native provider long press must keep its complete stream", longPressed.get())
    }

    @Test fun horizontalSwipeAcrossScrollableWidgetStillPages() {
        lateinit var pager: PagerState
        compose.setContent {
            pager = rememberPagerState(pageCount = { 2 })
            val limits = androidx.compose.runtime.remember(pager) { PageGestureLimits(pager) }
            Box(Modifier.fillMaxSize().onePageGestures(pager, limits)) {
                HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = false) { page ->
                    if (page == 0) AndroidView(factory = { viewContext ->
                        val providerList = ScrollView(viewContext).apply {
                            addView(TextView(viewContext).apply {
                                text = List(100) { "Calendar event $it" }.joinToString("\n")
                            }, FrameLayout.LayoutParams(1800, 1800))
                        }
                        zeroPaddingHost(providerList)
                    }, modifier = Modifier.fillMaxSize().testTag("paging-widget"))
                }
            }
        }
        compose.onNodeWithTag("paging-widget").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals("Horizontal axis ownership must remain with the one-page pager", 1, pager.settledPage)
    }

    @Test fun launcherLongPressStillStartsDragAcrossScrollableWidget() {
        val drag = HomeDragState()
        val nativeClicked = AtomicBoolean(false)
        compose.setContent {
            Box(Modifier.fillMaxSize().homeDragInput(
                drag = drag,
                enabled = true,
                page = 0,
                onStart = {},
                onFinish = { drag.clear() },
            )) {
                Box(Modifier.fillMaxSize().dropRegion(
                    drag = drag,
                    target = DropTarget.Widget(7),
                    page = 0,
                    widgetId = 42,
                )) {
                    AndroidView(factory = { viewContext ->
                        val providerList = ListView(viewContext).apply {
                            adapter = ArrayAdapter(
                                viewContext,
                                android.R.layout.simple_list_item_1,
                                List(100) { "Calendar event $it" },
                            )
                            onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, _, _ ->
                                nativeClicked.set(true)
                            }
                        }
                        zeroPaddingHost(providerList)
                    }, modifier = Modifier.fillMaxSize().testTag("draggable-widget"))
                }
            }
        }
        compose.waitForIdle()

        val widget = compose.onNodeWithTag("draggable-widget")
        widget.performTouchInput { down(center) }
        // A real native ListView continues dispatching/consuming tiny MOVE events during a
        // stationary hold. Put multiple samples before the long-press deadline; advancing
        // directly past the deadline before the first MOVE would miss the regression.
        repeat(10) { sample ->
            widget.performTouchInput {
                advanceEventTime(80)
                moveBy(Offset(0f, if (sample % 2 == 0) 2f else -2f))
            }
        }
        widget.performTouchInput {
            advanceEventTime(800)
            moveBy(Offset(0f, 1f))
        }
        compose.waitForIdle()
        assertTrue("Launcher long press must start the widget drag", drag.active)

        widget.performTouchInput { up() }
        compose.waitForIdle()
        assertFalse(drag.active)
        assertFalse("Launcher claim must cancel the native row click", nativeClicked.get())
    }

    @Test fun scrollableWidgetMovementBeforeLongPressDoesNotStartDrag() {
        val drag = HomeDragState()
        compose.setContent {
            Box(Modifier.fillMaxSize().homeDragInput(
                drag = drag,
                enabled = true,
                page = 0,
                onStart = {},
                onFinish = { drag.clear() },
            )) {
                Box(Modifier.fillMaxSize().dropRegion(
                    drag = drag,
                    target = DropTarget.Widget(8),
                    page = 0,
                    widgetId = 43,
                )) {
                    AndroidView(factory = { viewContext ->
                        zeroPaddingHost(ListView(viewContext).apply {
                            adapter = ArrayAdapter(
                                viewContext,
                                android.R.layout.simple_list_item_1,
                                List(100) { "Calendar event $it" },
                            )
                        })
                    }, modifier = Modifier.fillMaxSize().testTag("moving-widget"))
                }
            }
        }
        compose.waitForIdle()

        val widget = compose.onNodeWithTag("moving-widget")
        widget.performTouchInput { down(center) }
        widget.performTouchInput {
            advanceEventTime(100)
            moveBy(Offset(0f, -80f))
            advanceEventTime(650)
        }
        compose.waitForIdle()

        assertFalse("A provider scroll before timeout must cancel launcher drag", drag.active)
        widget.performTouchInput { cancel() }
        compose.waitForIdle()
        assertFalse(drag.active)
    }

    @Test fun secondPointerBeforeWidgetLongPressCancelsDrag() {
        val drag = HomeDragState()
        compose.setContent {
            Box(Modifier.fillMaxSize().homeDragInput(
                drag = drag,
                enabled = true,
                page = 0,
                onStart = {},
                onFinish = { drag.clear() },
            )) {
                Box(Modifier.fillMaxSize().dropRegion(
                    drag = drag,
                    target = DropTarget.Widget(9),
                    page = 0,
                    widgetId = 44,
                )) {
                    AndroidView(factory = { viewContext ->
                        zeroPaddingHost(ListView(viewContext).apply {
                            adapter = ArrayAdapter(
                                viewContext,
                                android.R.layout.simple_list_item_1,
                                List(100) { "Calendar event $it" },
                            )
                        })
                    }, modifier = Modifier.fillMaxSize().testTag("multitouch-widget"))
                }
            }
        }
        compose.waitForIdle()

        val widget = compose.onNodeWithTag("multitouch-widget")
        widget.performTouchInput { down(center) }
        widget.performTouchInput {
            advanceEventTime(100)
            down(pointerId = 1, position = center + Offset(20f, 0f))
            advanceEventTime(650)
        }
        compose.waitForIdle()

        assertFalse("A second pointer before timeout must cancel launcher drag", drag.active)
        widget.performTouchInput { cancel() }
    }
}
