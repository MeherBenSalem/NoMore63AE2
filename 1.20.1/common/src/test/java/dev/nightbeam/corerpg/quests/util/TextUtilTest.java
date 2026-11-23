package dev.nightbeam.corerpg.quests.util;

import dev.nightbeam.corerpg.quests.McTest;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextUtilTest extends McTest {
    @Test
    void ampersandCodesBecomeSectionSigns() {
        assertEquals("§6Gold §lbold§r", TextUtil.toSection("&6Gold &lbold&r"));
        assertEquals("§aupper", TextUtil.toSection("&Aupper"));
        assertEquals("Salt & pepper", TextUtil.toSection("Salt & pepper"));
        assertEquals("&6 stays", TextUtil.toSection("\\&6 stays"));
    }

    @Test
    void plainStripsCodes() {
        assertEquals("Gold bold", TextUtil.plain("&6Gold &lbold"));
        assertEquals("", TextUtil.plain(null));
    }

    @Test
    void formatHandlesLangPrefix() {
        assertInstanceOf(TranslatableContents.class, TextUtil.format("lang:quest.example").getContents());
        assertEquals("§cHi", TextUtil.format("&cHi").getString());
        assertEquals("", TextUtil.format("").getString());
    }

    @Test
    void blankLines() {
        assertTrue(TextUtil.isBlank(List.of("", "  ")));
        assertFalse(TextUtil.isBlank(List.of("", "x")));
    }

    @Test
    void durations() {
        assertEquals("0s", TextUtil.duration(0));
        assertEquals("42s", TextUtil.duration(42));
        assertEquals("1m 5s", TextUtil.duration(65));
        assertEquals("1h 1m", TextUtil.duration(3660));
        assertEquals("2d 3h", TextUtil.duration(2 * 86400 + 3 * 3600 + 59));
    }

    @Test
    void compactNumbers() {
        assertEquals("999", TextUtil.compact(999));
        assertEquals("9,999", TextUtil.compact(9999));
        assertEquals("12.5k", TextUtil.compact(12_500));
        assertEquals("10k", TextUtil.compact(10_000));
        assertEquals("3.2M", TextUtil.compact(3_200_000));
        assertEquals("1B", TextUtil.compact(1_000_000_000));
    }
}
