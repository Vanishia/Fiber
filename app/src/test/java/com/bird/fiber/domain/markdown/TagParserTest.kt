package com.bird.fiber.domain.markdown

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class TagParserTest {
    private fun paths(text: String) = TagParser.parse(text).map { it.path }

    @Test fun recognizesUnicodeNumbersAndDeduplicatesCase() {
        val tags = TagParser.parse("#读书 #2026 #Android #android #项目/Android #项目/android #العربية #日本語")
        assertEquals(listOf("读书", "2026", "android", "项目/android", "العربية", "日本語"), tags.map { it.path })
        assertEquals("Android", tags[2].displayPath)
    }

    @Test fun headingsOnlyRemoveMarkers() {
        assertEquals(listOf("日记", "列表", "引用"), paths("# 今日记录 #日记\n\n## 今日记录 #日记 ##\n\n- ## 标题 #列表\n\n> ### 标题 #引用"))
        assertEquals(listOf("日记"), paths("\\# 今日记录 #日记"))
    }

    @Test fun excludesCodeBlocksAndInlineCode() {
        assertEquals(listOf("正文"), paths("""
            ```markdown
            #围栏
            ```

            ~~~
            #波浪
            ~~~

                #缩进

            `#行内` 和 ``#行内2`` #正文
        """.trimIndent()))
    }

    @Test fun excludesDestinationsAndUrlFragmentsButKeepsLabels() {
        assertEquals(listOf("链接", "图片", "正文"), paths(
            "[#链接](https://example.com/#地址) ![#图片](attachments/a#图片地址.png) " +
                "https://example.com/#锚点 <https://example.com/#自动链接> www.example.com/#裸网址 #正文"))
        assertEquals(listOf("链接文字"), paths("[#链接文字](#链接文字)"))
    }

    @Test fun ignoresEscapesAndWordInternalHashes() {
        assertEquals(listOf("有效"), paths("C# abc#tag ##标签 ＃全角 \\#转义 # 有空格 #有效"))
        assertEquals(listOf("有效"), paths("\\\\#有效"))
        assertTrue(paths("\\##标签").isEmpty())
    }

    @Test fun excludesFrontmatterAndHtmlComments() {
        assertEquals(listOf("正文"), paths("---\ntags: [#属性]\n---\n\n<!-- #注释\n#第二行 -->\n\n文字 <!-- #行内 --> #正文"))
        assertTrue(paths("---\ntags: #未闭合").isEmpty())
        assertEquals(listOf("外面"), paths("<!-- #里面 --> #外面"))
    }

    @Test fun punctuationTerminatesAndBrokenHierarchyKeepsLongestPrefix() {
        assertEquals(listOf("读书", "项目/android", "x-y_z/2026"), paths(
            "#读书，今天 #读书//心理学 #读书/ #项目/Android//UI #项目/Android/ #/心理学 #x-y_z/2026。"))
    }

    @Test fun manualInputRequiresEntireValidPath() {
        assertEquals(ParsedTag("项目/android", "项目/Android"), TagParser.manual(" #项目/Android "))
        listOf("", "#", "读书/", "读书//心理学", "/心理学", "读 书", "读书，", "##标签").forEach {
            assertNull(it, TagParser.manual(it))
        }
    }

    @Test fun rawUnderscoresRemainTagCharactersAndEntitiesDoNotCreateTags() {
        assertEquals(listOf("_tag_", "项目/_android_", "__"), paths("#_tag_ #项目/_Android_ #__"))
        assertEquals(listOf("a", "有效"), paths("&#35;伪标签 #a&#95;b #a\\_b #有效"))
    }

    @Test fun normalizationDoesNotDependOnDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals(listOf("index"), paths("#INDEX #index"))
            assertEquals("é", TagParser.manual("e\u0301")?.path)
            assertEquals(listOf("é", "ⅷ", "①"), paths("#e\u0301 #Ⅷ #①"))
        } finally { Locale.setDefault(original) }
    }
}
