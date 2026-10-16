package com.example.nihaogo.data.content

import com.example.nihaogo.data.ai.ScriptedBossEngine
import com.example.nihaogo.data.ai.ScriptedInterrogation
import com.example.nihaogo.data.user.GameRules
import com.example.nihaogo.speech.ChineseText
import com.example.nihaogo.speech.SpokenChinese
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Catches mistakes in special.json before they reach the app. */
class SpecialContentTest {

    private val special = ContentRepository.parseSpecial(File("src/main/assets/${ContentRepository.SPECIAL_PATH}").readText())
    private val categoryIds = ContentRepository.parse(File("src/main/assets/${ContentRepository.CATEGORIES_PATH}").readText())
        .categories.map { it.id }

    @Test
    fun detectiveHasTheFourPlannedScenes() {
        val kinds = special.detective.scenes.map { it::class }
        assertEquals(listOf(Scene.Find::class, Scene.Dialogue::class, Scene.Puzzle::class, Scene.Interrogate::class), kinds)
        assertEquals(ModeKind.Detective, special.detective.kind)
    }

    @Test
    fun careersAreTrainingShiftThenAiCustomer() {
        assertEquals(listOf("doctor", "flight"), special.careers.map { it.id })
        special.careers.forEach { career ->
            assertEquals(ModeKind.Career, career.kind)
            assertEquals(listOf(Scene.Training::class, Scene.Dialogue::class, Scene.Roleplay::class), career.scenes.map { it::class })
            assertEquals("${career.id} ranks", career.scenes.size, career.ranks.size)
        }
    }

    @Test
    fun modesUnlockFromRealCategories() {
        special.modes.forEach { assertTrue(it.id, it.unlockAfter in categoryIds) }
    }

    @Test
    fun everySceneTeachesSixToEightNewWords() {
        special.modes.forEach { mode ->
            mode.scenes.forEach { scene ->
                assertTrue("${mode.id}/${scene.titleTh}: ${scene.words.size} words", scene.words.size in 6..8)
                assertEquals("${mode.id}/${scene.titleTh}: duplicate word", scene.words.size, scene.words.map { it.hanzi }.toSet().size)
                scene.words.forEach { w -> assertTrue(w.hanzi, listOf(w.hanzi, w.pinyin, w.thai, w.emoji).all { it.isNotBlank() }) }
            }
        }
    }

    @Test
    fun findTargetsAreObjectsInTheScene() {
        special.modes.flatMap { it.scenes }.filterIsInstance<Scene.Find>().forEach { scene ->
            val hanzi = scene.words.map { it.hanzi }
            scene.targets.forEach { assertTrue(it.hanzi, it.hanzi in hanzi) }
            assertEquals("targets must be distinct", scene.targets.size, scene.targets.map { it.hanzi }.toSet().size)
        }
    }

    @Test
    fun dialogueAnswersAndTasksAreValid() {
        special.modes.flatMap { it.scenes }.filterIsInstance<Scene.Dialogue>().forEach { scene ->
            assertTrue(scene.titleTh, scene.turns.isNotEmpty())
            scene.turns.forEach { turn ->
                assertTrue(turn.npc.hanzi, turn.answer in turn.options.indices)
                assertTrue(turn.npc.hanzi, turn.speaker != null || scene.npc != null)
                turn.task?.let { task ->
                    assertTrue(task.promptTh, task.answer in task.options.indices)
                    assertEquals(task.promptTh, task.options.size, task.options.map { it.hanzi }.toSet().size)
                }
            }
        }
    }

    @Test
    fun puzzleTilesSpellTheirSentences() {
        special.modes.flatMap { it.scenes }.filterIsInstance<Scene.Puzzle>().forEach { scene ->
            assertTrue(scene.clueTh.isNotBlank())
            scene.sentences.forEach { s ->
                assertEquals(s.hanzi, ChineseText.normalize(s.hanzi), ChineseText.normalize(s.tiles.joinToString("")))
                assertTrue("${s.hanzi}: distractor is also a tile", s.distractors.none { it in s.tiles })
            }
        }
    }

    @Test
    fun puzzleSentencesOfferOtherWaysToSayThem() {
        special.modes.flatMap { it.scenes }.filterIsInstance<Scene.Puzzle>().flatMap { it.sentences }.forEach { s ->
            assertTrue("${s.hanzi}: no alternative", s.alternatives.isNotEmpty())
            s.alternatives.forEach { alt ->
                assertTrue(alt.hanzi, listOf(alt.hanzi, alt.pinyin, alt.thai).all { it.isNotBlank() })
                assertFalse("${alt.hanzi} repeats the sentence", ChineseText.normalize(alt.hanzi) == ChineseText.normalize(s.hanzi))
            }
        }
    }

    @Test
    fun puzzleAcceptsAnotherWordOrder() {
        val puzzle = special.detective.scenes.filterIsInstance<Scene.Puzzle>().first()
        val garden = puzzle.sentences.first { it.tiles.contains("下午") }
        // 下午 before the subject is just as right as after it.
        val reordered = listOf("下午", "我们", "在", "花园", "等", "你").joinToString("")
        assertTrue(ChineseText.normalize(reordered) in garden.acceptedTexts)
        assertFalse(ChineseText.normalize("我们下午在厨房等你") in garden.acceptedTexts)
    }

    @Test
    fun scriptedSuspectsUnderstandSpokenQuestions() = runBlocking {
        val engine = ScriptedInterrogation()
        special.detective.suspects.forEach { s ->
            val q = s.questions.first()
            val spoken = "请问" + q.question.hanzi.trimEnd('？', '?', '。') + "呀？"
            val reply = engine.ask(s, spoken)
            assertTrue("${s.id}: $spoken", reply.understood)
            assertEquals(q.answer, reply.line)
            // The learner is shown another way to ask the same thing.
            assertTrue(reply.alternative != null && !SpokenChinese.sameMeaning(reply.alternative!!.hanzi, spoken))
        }
    }

    @Test
    fun specialCustomersSuggestAnotherWayEveryTime() = runBlocking {
        special.modes.flatMap { it.scenes }.filterIsInstance<Scene.Roleplay>().forEach { scene ->
            val engine = ScriptedBossEngine(scene.boss, flexible = true)
            engine.start()
            scene.boss.script.forEachIndexed { i, turn ->
                val judgement = engine.answer(turn.sampleHanzi, isFinal = i == scene.boss.script.lastIndex)
                assertTrue(judgement.correct)
                assertTrue("${scene.boss.name} turn $i", judgement.alternative != null)
            }
        }
        // Category bosses stay as they were.
        val strict = ScriptedBossEngine(special.careers.first().scenes.filterIsInstance<Scene.Roleplay>().first().boss)
        strict.start()
        assertEquals(null, strict.answer("你好", isFinal = false).alternative)
    }

    @Test
    fun detectiveCaseIsSolvable() {
        val d = special.detective
        assertTrue(d.culprit in d.suspects.map { it.id })
        assertTrue(d.confession != null && d.endingTh.isNotBlank())
        assertTrue("clues to find", d.scenes.flatMap { it.clues }.size >= 5)
        d.suspects.forEach { s ->
            assertTrue(s.id, s.questions.size >= 3)
            assertTrue(s.id, s.secret.isNotBlank() && s.alibi.hanzi.isNotBlank())
        }
        val needed = (d.scenes.last() as Scene.Interrogate).requiredQuestions
        assertTrue("enough suggested questions to reach $needed", d.suspects.sumOf { it.questions.size } >= needed)
    }

    @Test
    fun scriptedSuspectsAnswerTheirOwnSuggestedQuestions() = runBlocking {
        val engine = ScriptedInterrogation()
        special.detective.suspects.forEach { s ->
            s.questions.forEach { q ->
                val reply = engine.ask(s, q.question.hanzi)
                assertTrue("${s.id}: ${q.question.hanzi}", reply.understood)
                assertEquals(q.answer, reply.line)
            }
        }
    }

    @Test
    fun scriptedSuspectsRejectNonsense() = runBlocking {
        val s = special.detective.suspects.first()
        assertFalse(ScriptedInterrogation().ask(s, "hello").understood)
        assertFalse(ScriptedInterrogation().ask(s, "我喜欢吃苹果和香蕉").understood)
    }

    @Test
    fun roleplayCustomersAcceptTheirSampleAnswers() = runBlocking {
        special.modes.flatMap { it.scenes }.filterIsInstance<Scene.Roleplay>().forEach { scene ->
            assertEquals(scene.boss.name, GameRules.BOSS_ROUNDS, scene.boss.script.size)
            val engine = ScriptedBossEngine(scene.boss)
            engine.start()
            scene.boss.script.forEachIndexed { i, turn ->
                val judgement = engine.answer(turn.sampleHanzi, isFinal = i == scene.boss.script.lastIndex)
                assertTrue("${scene.boss.name} turn $i sample '${turn.sampleHanzi}' rejected", judgement.correct)
            }
        }
    }

    @Test
    fun detectiveSuspectsIntroduceThemselvesInDistinctVoices() {
        val suspects = special.detective.suspects
        suspects.forEach { s ->
            val intro = s.intro
            assertTrue("${s.id} has no intro", intro != null && listOf(intro.hanzi, intro.pinyin, intro.thai).all { it.isNotBlank() })
            assertTrue("${s.id} intro should say their name", s.name in intro!!.hanzi)
            assertTrue("${s.id} pitch ${s.voice.pitch}", s.voice.pitch in 0.5f..2f)
            assertTrue("${s.id} rate ${s.voice.rate}", s.voice.rate in 0.7f..1.5f)
        }
        assertEquals("each suspect sounds different", suspects.size, suspects.map { it.voice }.toSet().size)
        assertTrue(special.detective.storyTh.isNotEmpty())
    }
}
