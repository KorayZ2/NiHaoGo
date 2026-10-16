package com.example.nihaogo.ui.special

import com.example.nihaogo.data.content.ContentRepository
import com.example.nihaogo.data.content.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Keeps the mansion's paintings in step with special.json. */
class CaseArtTest {

    private val case = ContentRepository.parseSpecial(File("src/main/assets/${ContentRepository.SPECIAL_PATH}").readText()).detective
    private val art = checkNotNull(caseArt(case.id))

    @Test
    fun everySceneHasAPainting() {
        assertEquals(case.scenes.size, art.scenes.size)
    }

    @Test
    fun everySuspectCanBeTappedOnTheIntro() {
        assertEquals(case.suspects.map { it.id }.toSet(), art.spots.keys)
    }

    @Test
    fun everyWordOfAPaintedFindSceneIsSomewhereInThePainting() {
        art.findSpots.forEach { (index, spots) ->
            val scene = case.scenes[index] as Scene.Find
            assertEquals(scene.titleTh, scene.words.map { it.hanzi }.toSet(), spots.keys)
            spots.forEach { (hanzi, rects) ->
                assertTrue(hanzi, rects.isNotEmpty())
                rects.forEach { r ->
                    assertTrue("$hanzi $r", r.left >= 0f && r.top >= 0f && r.right <= 1f && r.bottom <= 1f && !r.isEmpty)
                }
            }
        }
        assertNotNull("the front of the mansion is played on its painting", findSpots(case.id, 0))
    }
}
