package com.margelo.nitro.betterpdf

import com.margelo.nitro.betterpdf.PrefetchPlanner.Direction.BACKWARD
import com.margelo.nitro.betterpdf.PrefetchPlanner.Direction.FORWARD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchPlannerTest {
  @Test
  fun `visible first, then ahead in the scroll direction, then behind`() {
    assertEquals(listOf(20, 21, 22, 19, 18), PrefetchPlanner.plan(20..20, 20, 100, FORWARD))
    assertEquals(listOf(20, 19, 18, 21, 22), PrefetchPlanner.plan(20..20, 20, 100, BACKWARD))
    assertEquals(listOf(20, 21, 22, 23, 19), PrefetchPlanner.plan(20..20, 20, 100, FORWARD, ahead = 3, behind = 1))
  }

  @Test
  fun `the page nearest the center renders first, then neighbours in scroll order`() {
    assertEquals(listOf(11, 12, 10, 13, 14, 9, 8), PrefetchPlanner.plan(10..12, 11, 100, FORWARD))
    assertEquals(listOf(11, 10, 12, 9, 8, 13, 14), PrefetchPlanner.plan(10..12, 11, 100, BACKWARD))
  }

  @Test
  fun `clamps at document bounds`() {
    assertEquals(listOf(0, 1, 2), PrefetchPlanner.plan(0..0, 0, 3, FORWARD))
    assertEquals(listOf(2, 1, 0), PrefetchPlanner.plan(2..2, 2, 3, BACKWARD))
    assertEquals(listOf(0), PrefetchPlanner.plan(0..0, 0, 1, FORWARD))
  }

  @Test
  fun `no duplicates`() {
    val plan = PrefetchPlanner.plan(3..6, 5, 8, FORWARD, ahead = 5, behind = 5)
    assertEquals(plan.distinct(), plan)
    assertEquals((0..7).toSet(), plan.toSet())
  }

  @Test
  fun `empty inputs produce an empty plan`() {
    assertEquals(emptyList<Int>(), PrefetchPlanner.plan(IntRange.EMPTY, 0, 10, FORWARD))
    assertEquals(emptyList<Int>(), PrefetchPlanner.plan(0..1, 0, 0, FORWARD))
  }

  @Test
  fun `a center outside the visible range is clamped into it`() {
    assertEquals(6, PrefetchPlanner.plan(4..6, 99, 10, FORWARD).first())
    assertEquals(4, PrefetchPlanner.plan(4..6, -3, 10, FORWARD).first())
  }

  @Test
  fun `the plan never grows with document size`() {
    for (pageCount in listOf(10, 1_000, 100_000)) {
      val plan = PrefetchPlanner.plan(5..7, 6, pageCount, FORWARD)
      assertTrue(plan.size <= 3 + PrefetchPlanner.DEFAULT_AHEAD + PrefetchPlanner.DEFAULT_BEHIND)
    }
  }

  @Test
  fun `the budget grows to cover the whole plan so planned pages never evict each other`() {
    val fit = PrefetchPlanner.fitToBudget(List(6) { 7L }, minKeep = 2, baseBudget = 32, maxBudget = 128)
    assertEquals(6, fit.keep)
    assertEquals(42L, fit.budgetBytes)
  }

  @Test
  fun `the base budget is kept when the plan is smaller`() {
    val fit = PrefetchPlanner.fitToBudget(List(3) { 7L }, minKeep = 1, baseBudget = 32, maxBudget = 128)
    assertEquals(3, fit.keep)
    assertEquals(32L, fit.budgetBytes)
  }

  @Test
  fun `beyond the maximum the lowest-priority pages are dropped, never visible ones`() {
    val fit = PrefetchPlanner.fitToBudget(List(10) { 20L }, minKeep = 3, baseBudget = 32, maxBudget = 50)
    assertEquals(3, fit.keep) // visible pages exceed the cap on their own
    val roomy = PrefetchPlanner.fitToBudget(List(10) { 20L }, minKeep = 1, baseBudget = 32, maxBudget = 70)
    assertEquals(3, roomy.keep)
    assertEquals(60L, roomy.budgetBytes)
  }

  @Test
  fun `under memory pressure only visible pages are kept`() {
    val fit = PrefetchPlanner.fitToBudget(List(6) { 7L }, minKeep = 2, baseBudget = 0, maxBudget = 0)
    assertEquals(2, fit.keep)
    assertEquals(0L, fit.budgetBytes)
  }
}
