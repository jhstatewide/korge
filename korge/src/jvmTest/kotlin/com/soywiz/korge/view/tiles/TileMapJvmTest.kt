package com.soywiz.korge.view.tiles

import com.soywiz.kds.IntArray2
import com.soywiz.korag.log.LogAG
import com.soywiz.korag.log.LogBaseAG
import com.soywiz.korge.test.assertEqualsFileReference
import com.soywiz.korge.view.scale
import com.soywiz.korge.view.viewsLog
import com.soywiz.korim.bitmap.Bitmap32
import com.soywiz.korim.bitmap.slice
import org.junit.Test

class TileMapJvmTest {
    @Test
    fun test() {
        viewsLog {
            val log = it.ag as LogAG
            log.logFilter = { str, kind -> kind != LogBaseAG.Kind.DRAW_DETAILS && kind != LogBaseAG.Kind.SHADER }
            views.stage.tileMap(IntArray2(100, 100) { 0 }, TileSet.fromBitmapSlices(32, 32, listOf(Bitmap32(32, 32).slice()))).scale(0.1)
            it.views.render()
            assertEqualsFileReference(
                "korge/render/TileMapTest.log",
                log.getLogAsString()
            )
        }
    }
}
