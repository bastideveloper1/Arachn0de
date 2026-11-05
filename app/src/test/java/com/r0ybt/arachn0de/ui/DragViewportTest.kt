package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DragViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun grabbedCardKeepsSizeAndFollowsFingerWithoutChangingOrder() {
        val list=LazyListState()
        val motor=DragReorderState(list,"node:")
        val order=listOf("a","b")
        var commits=0
        motor.groups=mapOf(false to order)
        motor.commit={_,_,_->commits++}
        compose.setContent {
            LazyColumn(state=list,modifier=Modifier.height(220.dp)) {
                items(order,key={"node:$it"}) {id->
                    Text(id,Modifier.height(100.dp).then(motor.cardModifier(id)).testTag(id))
                }
            }
        }
        val before=compose.onNodeWithTag("a").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val item=list.layoutInfo.visibleItemsInfo.first()
            motor.startDrag(item.offset+item.size/2f)
            motor.dragTo(motor.fingerY+12f)
        }
        val during=compose.onNodeWithTag("a").fetchSemanticsNode().boundsInRoot
        assertEquals(before.width,during.width,.1f)
        assertEquals(before.height,during.height,.1f)
        assertEquals(before.top+12f,during.top,.1f)
        compose.runOnIdle {motor.finishDrag()}
        val after=compose.onNodeWithTag("a").fetchSemanticsNode().boundsInRoot
        assertEquals(before,after)
        assertEquals(order,motor.orderFor(false,order))
        assertEquals(0,commits)
    }

    @Test fun movingToZeroKeepsViewportAndPersistsSameOrder() {
        val list = LazyListState()
        val motor = DragReorderState(list, "node:")
        var persisted by mutableStateOf(listOf("a", "b", "c"))
        compose.setContent {
            SideEffect {
                motor.groups = mapOf(false to persisted)
                motor.commit = { source, target, _ -> persisted = persisted.moveDraggedToTarget(source, target) }
                motor.reconcile(null)
            }
            LazyColumn(state = list, modifier = Modifier.height(180.dp)) {
                items(motor.orderFor(false, persisted), key = { "node:$it" }) {
                    Text(it, Modifier.height(100.dp))
                }
            }
        }
        compose.runOnIdle {
            val first = list.layoutInfo.visibleItemsInfo.first { it.key == "node:a" }
            val second = list.layoutInfo.visibleItemsInfo.first { it.key == "node:b" }
            motor.startDrag(second.offset + second.size / 2f)
            motor.dragTo(first.offset + first.size / 2f)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("node:b", list.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, list.firstVisibleItemScrollOffset)
            motor.finishDrag()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("b", "a", "c"), persisted)
            assertEquals("node:b", list.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(0, list.firstVisibleItemScrollOffset)
            assertNull(motor.draggedId)
        }
    }
}
