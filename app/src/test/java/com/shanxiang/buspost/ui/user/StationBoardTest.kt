package com.shanxiang.buspost.ui.user

import com.shanxiang.buspost.data.model.BusLine
import com.shanxiang.buspost.data.model.Geo
import com.shanxiang.buspost.data.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 站牌纯层契约（`StationBoard.kt`）。
 *
 * 钉的是三件最容易悄悄坏掉的事：
 * ① 后端没给 `plannedMinutes` 时**不许塌成 0 再念"0 分钟"**（那等于承诺"车已经到了"）；
 * ② 起点站要念"首发站"而不是给一个到站分钟；
 * ③ 排序必须"有分钟数的按早到晚在前、没给的整体沉底"，沉底那批彼此保持后端顺序。
 */
class StationBoardTest {

    private fun stop(name: String, minutes: Int = 0, id: Long = name.hashCode().toLong()) =
        Station(id = id, name = name, geo = Geo(0.0, 0.0), plannedMinutes = minutes)

    private fun line(id: String, from: String, to: String, vararg stops: Station) = BusLine(
        id = id,
        name = "$id 路",
        from = from,
        to = to,
        stations = stops.toList(),
        vehicles = emptyList(),
        dataSource = "PROJECT",
    )

    @Test
    fun `只收停靠这一站的线路，终点方向按站在哪一头给`() {
        val lines = listOf(
            line("347", "老厂", "菜园坝", stop("老厂首发"), stop("文峰公社", 6), stop("南坪")),
            line("346", "悠山路", "较场口", stop("海棠溪")),
        )
        val board = stationBoard(lines, "文峰公社")
        assertEquals(1, board.size)
        assertEquals("347 路", board[0].routeName)
        // 这一站不是终点站 ⇒ 报的是"开往终点"
        assertEquals("菜园坝", board[0].terminal)
        assertEquals(6, board[0].minutes)
    }

    @Test
    fun `后端没给 plannedMinutes 时是 null，不是 0`() {
        val board = stationBoard(
            listOf(line("347", "老厂", "菜园坝", stop("文峰公社"), stop("南坪"))),
            "文峰公社",
        )
        // idx==0 走"首发站"那一支；这里再单独验一个中间站没给分钟数的情况
        val mid = stationBoard(
            listOf(line("347", "老厂", "菜园坝", stop("起点"), stop("文峰公社", 0))),
            "文峰公社",
        )
        assertTrue(board.isEmpty() || board[0].isOrigin)
        assertNull(mid[0].minutes)
        assertEquals("到站时间没给", mid[0].arrivalText())
    }

    @Test
    fun `起点站念首发站，且不许同时给出分钟数`() {
        val board = stationBoard(
            listOf(line("347", "文峰公社", "菜园坝", stop("文峰公社", 3), stop("南坪"))),
            "文峰公社",
        )
        assertTrue(board[0].isOrigin)
        assertNull(board[0].minutes)
        assertEquals("首发站 · 从这上车", board[0].arrivalText())
        // 起点站的"开往"必须是终点，不是"从哪来"
        assertEquals("菜园坝", board[0].terminal)
    }

    @Test
    fun `有分钟数的按早到晚在前，没给的沉底且保持后端顺序`() {
        val lines = listOf(
            line("C", "甲", "乙", stop("起点"), stop("文峰公社", 12)),
            line("A", "甲", "乙", stop("起点"), stop("文峰公社", 4)),
            line("B", "甲", "乙", stop("起点"), stop("文峰公社")),
            line("D", "甲", "乙", stop("起点"), stop("文峰公社", 9)),
        )
        val board = stationBoard(lines, "文峰公社")
        assertEquals(listOf("A 路", "D 路", "C 路", "B 路"), board.map { it.routeName })
        assertEquals(listOf(4, 9, 12, null), board.map { it.minutes })
    }

    @Test
    fun `站名为空不查，返回空而不是把全部线路念成途经`() {
        val lines = listOf(line("347", "老厂", "菜园坝", stop("文峰公社", 6)))
        assertTrue(stationBoard(lines, "").isEmpty())
        assertTrue(stationBoard(lines, "   ").isEmpty())
    }
}
