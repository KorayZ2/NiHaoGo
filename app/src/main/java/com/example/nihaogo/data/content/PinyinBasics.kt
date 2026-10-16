package com.example.nihaogo.data.content

/**
 * The pinyin primer shown on first launch: initials, finals, tones and tone changes.
 * Fixed teaching content, so it lives in code rather than in the content JSON.
 */
object PinyinBasics {

    /** One initial or final, with a Thai sound-alike and a word to hear it in. */
    data class Sound(val pinyin: String, val thai: String, val hanzi: String, val example: String, val meaningTh: String)

    data class SoundGroup(val titleTh: String, val sounds: List<Sound>)

    /**
     * [pitch] is the tone's contour on the 1 (low) to 5 (high) scale; a single value is a short
     * sound with no movement (the neutral tone).
     */
    data class Tone(
        val number: Int,
        val nameTh: String,
        val hanzi: String,
        val pinyin: String,
        val meaningTh: String,
        val howTh: String,
        val pitch: List<Int>,
    )

    /** [spoken] is null when the word is said the way it is written. */
    data class ChangeExample(val hanzi: String, val written: String, val spoken: String?, val meaningTh: String)

    data class ToneChange(val titleTh: String, val ruleTh: String, val examples: List<ChangeExample>)

    data class QuizItem(val hanzi: String, val pinyin: String, val meaningTh: String, val tone: Int)

    val initials = listOf(
        SoundGroup(
            "ริมฝีปาก",
            listOf(
                Sound("b", "ป", "八", "bā", "แปด"),
                Sound("p", "พ", "怕", "pà", "กลัว"),
                Sound("m", "ม", "妈", "mā", "แม่"),
                Sound("f", "ฟ", "飞", "fēi", "บิน"),
            ),
        ),
        SoundGroup(
            "ปลายลิ้นแตะเหงือก",
            listOf(
                Sound("d", "ต", "大", "dà", "ใหญ่"),
                Sound("t", "ท", "他", "tā", "เขา"),
                Sound("n", "น", "你", "nǐ", "คุณ"),
                Sound("l", "ล", "来", "lái", "มา"),
            ),
        ),
        SoundGroup(
            "โคนลิ้น",
            listOf(
                Sound("g", "ก", "哥", "gē", "พี่ชาย"),
                Sound("k", "ค", "看", "kàn", "ดู"),
                Sound("h", "ฮ", "好", "hǎo", "ดี"),
            ),
        ),
        SoundGroup(
            "ลิ้นแตะเพดาน (ตามด้วย i หรือ ü)",
            listOf(
                Sound("j", "จ", "鸡", "jī", "ไก่"),
                Sound("q", "ช", "七", "qī", "เจ็ด"),
                Sound("x", "ซ", "西", "xī", "ตะวันตก"),
            ),
        ),
        SoundGroup(
            "ม้วนลิ้น",
            listOf(
                Sound("zh", "จ", "猪", "zhū", "หมู"),
                Sound("ch", "ช", "吃", "chī", "กิน"),
                Sound("sh", "ซ", "书", "shū", "หนังสือ"),
                Sound("r", "ร", "热", "rè", "ร้อน"),
            ),
        ),
        SoundGroup(
            "ปลายลิ้นแตะหลังฟัน",
            listOf(
                Sound("z", "จ", "字", "zì", "ตัวอักษร"),
                Sound("c", "ช", "菜", "cài", "ผัก"),
                Sound("s", "ซ", "三", "sān", "สาม"),
            ),
        ),
        SoundGroup(
            "กึ่งพยัญชนะ",
            listOf(
                Sound("y", "ย", "有", "yǒu", "มี"),
                Sound("w", "ว", "我", "wǒ", "ฉัน"),
            ),
        ),
    )

    val initialNotesTh = listOf(
        "b d g ใกล้เสียง ป ต ก ไม่ใช่ บ ด",
        "p t k q c ch ต้องพ่นลมออกมาแรง ๆ ลองเอามือป้องปากดูจะรู้สึกได้",
        "zh ch sh r ให้ม้วนปลายลิ้นขึ้นไปแตะเพดาน",
    )

    val finals = listOf(
        SoundGroup(
            "สระเดี่ยว",
            listOf(
                Sound("a", "อา", "他", "tā", "เขา"),
                Sound("o", "ออ", "波", "bō", "คลื่น"),
                Sound("e", "เออ", "饿", "è", "หิว"),
                Sound("i", "อี", "米", "mǐ", "ข้าวสาร"),
                Sound("u", "อู", "路", "lù", "ถนน"),
                Sound("ü", "อวี", "女", "nǚ", "ผู้หญิง"),
                Sound("er", "เออร์", "二", "èr", "สอง"),
            ),
        ),
        SoundGroup(
            "สระผสม",
            listOf(
                Sound("ai", "ไอ", "爱", "ài", "รัก"),
                Sound("ei", "เอย", "北", "běi", "เหนือ"),
                Sound("ao", "เอา", "好", "hǎo", "ดี"),
                Sound("ou", "โอว", "狗", "gǒu", "หมา"),
                Sound("ia", "อี+อา", "家", "jiā", "บ้าน"),
                Sound("ie", "อี+เอ", "谢", "xiè", "ขอบคุณ"),
                Sound("ua", "วา", "花", "huā", "ดอกไม้"),
                Sound("uo", "วอ", "多", "duō", "มาก"),
                Sound("üe", "อวี+เอ", "学", "xué", "เรียน"),
                Sound("iao", "เอียว", "小", "xiǎo", "เล็ก"),
                Sound("iu", "อิว", "六", "liù", "หก"),
                Sound("uai", "วาย", "快", "kuài", "เร็ว"),
                Sound("ui", "เวย", "对", "duì", "ถูกต้อง"),
            ),
        ),
        SoundGroup(
            "สระเสียงนาสิก (ลงท้าย n / ng)",
            listOf(
                Sound("an", "อัน", "饭", "fàn", "ข้าว"),
                Sound("en", "เอิน", "门", "mén", "ประตู"),
                Sound("ang", "อัง", "忙", "máng", "ยุ่ง"),
                Sound("eng", "เอิง", "冷", "lěng", "หนาว"),
                Sound("ong", "อง", "中", "zhōng", "กลาง"),
                Sound("in", "อิน", "新", "xīn", "ใหม่"),
                Sound("ing", "อิง", "听", "tīng", "ฟัง"),
                Sound("ian", "เอียน", "天", "tiān", "ฟ้า"),
                Sound("iang", "เอียง", "两", "liǎng", "สอง"),
                Sound("iong", "อี+อง", "熊", "xióng", "หมี"),
                Sound("uan", "วาน", "短", "duǎn", "สั้น"),
                Sound("uang", "วาง", "黄", "huáng", "เหลือง"),
                Sound("un", "อุน", "春", "chūn", "ฤดูใบไม้ผลิ"),
                Sound("ün", "อวิน", "云", "yún", "เมฆ"),
                Sound("üan", "อวีเอน", "远", "yuǎn", "ไกล"),
            ),
        ),
    )

    val finalNotesTh = listOf(
        "ü ทำปากจู๋เหมือนพูด อู แล้วออกเสียง อี",
        "หลัง j q x y ตัว ü เขียนเป็น u (ไม่มีจุด) แต่ยังอ่านเป็น ü เช่น 去 qù, 学 xué",
        "i หลัง z c s zh ch sh r ไม่อ่านว่า อี แต่เป็นเสียง อึ เช่น 四 sì, 是 shì",
        "iu ui un เป็นรูปย่อของ iou uei uen",
        "ถ้าไม่มีพยัญชนะต้น i u ü จะเขียนเป็น y w yu เช่น 一 yī, 五 wǔ, 鱼 yú",
    )

    val tones = listOf(
        Tone(1, "เสียง 1", "妈", "mā", "แม่", "สูงและลากยาวเท่ากัน คล้าย \"มา\" แต่สูงค้างไว้", listOf(5, 5)),
        Tone(2, "เสียง 2", "麻", "má", "ป่าน", "ขึ้นจากกลางไปสูง คล้ายเสียงจัตวา \"หมา\"", listOf(3, 5)),
        Tone(3, "เสียง 3", "马", "mǎ", "ม้า", "ลงต่ำแล้วเชิดขึ้น คล้ายเสียงเอก \"หม่า\"", listOf(2, 1, 4)),
        Tone(4, "เสียง 4", "骂", "mà", "ด่า", "ตกจากสูงลงต่ำแรงและสั้น คล้ายเสียงโท \"ม่า\"", listOf(5, 1)),
        Tone(0, "เสียงเบา", "吗", "ma", "ไหม (คำถาม)", "เบาและสั้น ไม่มีเครื่องหมาย", listOf(3)),
    )

    /** A sentence using all the example words, to show that the tone changes the meaning. */
    const val toneSentence = "妈妈骂马吗？"
    const val toneSentencePinyin = "Māma mà mǎ ma?"
    const val toneSentenceTh = "แม่ด่าม้าหรือเปล่า?"

    const val markPlacementTh =
        "ใส่เครื่องหมายบน a ก่อน ถ้าไม่มี a ใส่บน o หรือ e ส่วน iu กับ ui ใส่บนตัวหลัง เช่น hǎo, duō, liù, duì"

    val toneChanges = listOf(
        ToneChange(
            "เสียง 3 + เสียง 3",
            "เสียง 3 สองตัวติดกัน ตัวแรกเปลี่ยนเป็นเสียง 2",
            listOf(
                ChangeExample("你好", "nǐ hǎo", "ní hǎo", "สวัสดี"),
                ChangeExample("很好", "hěn hǎo", "hén hǎo", "ดีมาก"),
                ChangeExample("水果", "shuǐguǒ", "shuíguǒ", "ผลไม้"),
            ),
        ),
        ToneChange(
            "不 bù",
            "ปกติเป็นเสียง 4 แต่ถ้าตามด้วยเสียง 4 จะเปลี่ยนเป็น bú",
            listOf(
                ChangeExample("不是", "bù shì", "bú shì", "ไม่ใช่"),
                ChangeExample("不要", "bù yào", "bú yào", "ไม่เอา"),
                ChangeExample("不好", "bù hǎo", null, "ไม่ดี"),
            ),
        ),
        ToneChange(
            "一 yī",
            "อยู่เดี่ยว ๆ หรือนับเลขอ่าน yī ตามด้วยเสียง 4 อ่าน yí ตามด้วยเสียง 1 2 3 อ่าน yì",
            listOf(
                ChangeExample("一个", "yī gè", "yí gè", "หนึ่งอัน"),
                ChangeExample("一天", "yī tiān", "yì tiān", "หนึ่งวัน"),
                ChangeExample("第一", "dì yī", null, "ที่หนึ่ง"),
            ),
        ),
    )

    /** Quiz words grouped by tone, all single characters the TTS reads with their usual tone. */
    private val quizPool = listOf(
        listOf(QuizItem("书", "shū", "หนังสือ", 1), QuizItem("天", "tiān", "ฟ้า", 1), QuizItem("飞", "fēi", "บิน", 1), QuizItem("三", "sān", "สาม", 1)),
        listOf(QuizItem("茶", "chá", "ชา", 2), QuizItem("来", "lái", "มา", 2), QuizItem("鱼", "yú", "ปลา", 2), QuizItem("十", "shí", "สิบ", 2)),
        listOf(QuizItem("水", "shuǐ", "น้ำ", 3), QuizItem("买", "mǎi", "ซื้อ", 3), QuizItem("马", "mǎ", "ม้า", 3), QuizItem("五", "wǔ", "ห้า", 3)),
        listOf(QuizItem("四", "sì", "สี่", 4), QuizItem("大", "dà", "ใหญ่", 4), QuizItem("爱", "ài", "รัก", 4), QuizItem("六", "liù", "หก", 4)),
    )

    /** One word for each of the four tones, in random order. */
    fun newQuiz(): List<QuizItem> = quizPool.map { it.random() }.shuffled()
}
