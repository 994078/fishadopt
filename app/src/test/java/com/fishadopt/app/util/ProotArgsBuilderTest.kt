package com.fishadopt.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotArgsBuilderTest {

    @Test
    fun buildsExpectedArgs() {
        val cfg = ProotArgsBuilder.Config(
            prootBinary = "/data/app/libproot_rs.so",
            rootfsPath = "/data/app/rootfs",
            externalStorageHost = "/storage/emulated/0",
        )
        val args = ProotArgsBuilder.build(cfg)

        assertEquals("/data/app/libproot_rs.so", args[0])
        assertTrue("必须包含 --link2symlink", args.contains("--link2symlink"))
        assertTrue(args.contains("-0"))

        assertEquals("-r", args[args.indexOf("-r")])
        assertEquals("/data/app/rootfs", args[args.indexOf("-r") + 1])

        // 系统目录绑定
        assertTrue(args.contains("/dev:/dev"))
        assertTrue(args.contains("/proc:/proc"))
        assertTrue(args.contains("/sys:/sys"))
        assertTrue(args.contains("/storage/emulated/0:/storage"))

        assertEquals("-w", args[args.indexOf("-w")])
        assertEquals("/root", args[args.indexOf("-w") + 1])

        assertTrue(args.contains("/bin/bash"))
        assertTrue(args.contains("/root/start_dsh.sh"))
    }
}
