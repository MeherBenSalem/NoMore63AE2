package dev.nightbeam.corerpg.quests.fabric;

import dev.nightbeam.corerpg.quests.gametest.QuestGameTests;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;

import java.util.Collection;

/** Fabric's game test entrypoint; the tests themselves live in common code and run on both loaders. */
public final class CoreQuestsFabricGameTests {
    @GameTestGenerator
    public Collection<TestFunction> tests() {
        return QuestGameTests.all();
    }
}
