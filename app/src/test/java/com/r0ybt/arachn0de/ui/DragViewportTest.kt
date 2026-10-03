package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
