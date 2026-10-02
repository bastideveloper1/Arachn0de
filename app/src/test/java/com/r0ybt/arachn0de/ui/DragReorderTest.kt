package com.r0ybt.arachn0de.ui

import org.junit.Assert.*
import org.junit.Test

class DragReorderTest {
    @Test fun finalTargetMatchesRepositoryInsertionForEverySourceAndDestination() {
        val original = listOf("a", "b", "c", "d", "e")
        for (source in original) for (target in original) {
            val visual = original.moveDraggedToTarget(source, target)
            val commitTarget = finalTarget(original, visual, source)
            if (source == target) assertNull(commitTarget)
            else assertEquals(visual, original.moveDraggedToTarget(source, checkNotNull(commitTarget)))
        }
    }

    @Test fun returningToOriginalSlotDoesNotCommitAndUnknownNeighborsCannotEnterGroup() {
        val original = listOf("a", "b", "c")
        val moved = original.moveDraggedToTarget("a", "c")
        assertNull(finalTarget(original, moved.moveDraggedToTarget("a", "b"), "a"))
        assertEquals(original, original.moveDraggedToTarget("a", "completed-other-group"))
    }

    @Test fun edgeSpeedIsContinuousQuadraticAndClamped() {
        assertEquals(0f, computeAutoScrollVelocity(100f, 600f, 100f, 700f), 0f)
        assertEquals(-175f, computeAutoScrollVelocity(50f, 600f, 100f, 700f), 0f)
        assertEquals(175f, computeAutoScrollVelocity(550f, 600f, 100f, 700f), 0f)
        assertEquals(700f, computeAutoScrollVelocity(900f, 600f, 100f, 700f), 0f)
        assertEquals(-700f, computeAutoScrollVelocity(-10f, 600f, 100f, 700f), 0f)
        assertEquals(0f, computeAutoScrollVelocity(0f, 0f, 100f, 700f), 0f)
    }
}
