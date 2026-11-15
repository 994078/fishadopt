package com.fishadopt.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VolumeMergerTest {

    @Test
    fun seekPositionLocatesCorrectVolume() {
        val sizes = listOf(10L, 20L, 30L)
        assertEquals(0 to 0L, VolumeMerger.seekPosition(sizes, 0))
        assertEquals(0 to 5L, VolumeMerger.seekPosition(sizes, 5))
        assertEquals(1 to 0L, VolumeMerger.seekPosition(sizes, 10))
        assertEquals(1 to 15L, VolumeMerger.seekPosition(sizes, 25))
        assertEquals(2 to 10L, VolumeMerger.seekPosition(sizes, 40))
        assertEquals(3 to 0L, VolumeMerger.seekPosition(sizes, 60)) // 完成
    }

    @Test
    fun mergeConcatenatesVolumesInOrder() {
        val dir = Files.createTempDirectory("vol").toFile()
        val v1 = File(dir, "v1").apply { writeText("hello") }
        val v2 = File(dir, "v2").apply { writeText("world") }
        val target = File(dir, "out")
        val progress = File(dir, "progress")

        val merger = VolumeMerger(
            listOf(FileVolumeSource(v1), FileVolumeSource(v2)), target, progress
        )
        assertTrue(merger.merge())
        assertEquals("helloworld", target.readText())
        assertFalse("完成后应删除进度文件", progress.exists())
    }

    @Test
    fun mergeResumesFromBreakpoint() {
        val dir = Files.createTempDirectory("vol").toFile()
        val v1 = File(dir, "v1").apply { writeText("hello") }
        val v2 = File(dir, "v2").apply { writeText("world") }
        val target = File(dir, "out")
        val progress = File(dir, "progress")

        // 模拟中断：已写入前 2 字节，进度记录 2
        target.writeText("he")
        progress.writeText("2")

        val merger = VolumeMerger(
            listOf(FileVolumeSource(v1), FileVolumeSource(v2)), target, progress
        )
        assertTrue(merger.merge())
        assertEquals("helloworld", target.readText())
        assertFalse(progress.exists())
    }

    @Test
    fun mergeRestartsWhenTargetTruncated() {
        val dir = Files.createTempDirectory("vol").toFile()
        val v1 = File(dir, "v1").apply { writeText("hello") }
        val v2 = File(dir, "v2").apply { writeText("world") }
        val target = File(dir, "out")
        val progress = File(dir, "progress")

        // 进度声称 5 字节，但目标文件为空（被截断），应回退重来
        progress.writeText("5")

        val merger = VolumeMerger(
            listOf(FileVolumeSource(v1), FileVolumeSource(v2)), target, progress
        )
        assertTrue(merger.merge())
        assertEquals("helloworld", target.readText())
    }
}
