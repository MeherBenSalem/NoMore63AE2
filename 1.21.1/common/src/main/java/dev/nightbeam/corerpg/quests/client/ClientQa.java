package dev.nightbeam.corerpg.quests.client;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.client.screen.FilterEditorScreen;
import dev.nightbeam.corerpg.quests.client.screen.QuestBookScreen;
import dev.nightbeam.corerpg.quests.client.screen.TeamScreen;
import dev.nightbeam.corerpg.quests.client.screen.editor.PropertyEditorScreen;
import dev.nightbeam.corerpg.quests.client.screen.editor.RewardTablesScreen;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Scripted client automation for QA and store screenshots. Inactive unless the system property
 * {@code corerpg_quests.qa.script} is set, e.g.
 * {@code -Dcorerpg_quests.qa.script="wait:60;open:;wait:20;screenshot:book;quit"}.
 * Steps: wait:ticks, open:focusId, screenshot:name, edit:on|off, team, tables, editor:objectId,
 * filter:expression, command:text (use + for spaces), pin:questId, vein:on|off, vein_shape:index, hud:on|off,
 * look:yaw,pitch, close, quit.
 */
public final class ClientQa {
    private static final Deque<String> STEPS = new ArrayDeque<>();
    private static boolean loaded;
    private static int wait;
    private static boolean started;

    private ClientQa() {
    }

    public static void tick(Minecraft mc) {
        if (!loaded) {
            loaded = true;
            String script = System.getProperty("corerpg_quests.qa.script", "");
            for (String step : script.split(";")) {
                if (!step.isBlank()) {
                    STEPS.add(step.trim());
                }
            }
            if (!STEPS.isEmpty()) {
                CoreQuests.LOG.info("QA script loaded with {} steps", STEPS.size());
            }
        }
        if (STEPS.isEmpty() || mc.player == null || !ClientQuests.ready()) {
            return;
        }
        if (!started) {
            started = true;
            wait = 40;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        String step = STEPS.poll();
        String name = step.contains(":") ? step.substring(0, step.indexOf(':')) : step;
        String arg = step.contains(":") ? step.substring(step.indexOf(':') + 1) : "";
        CoreQuests.LOG.info("QA step: {}", step);
        switch (name) {
            case "wait" -> wait = Integer.parseInt(arg);
            case "open" -> QuestBookScreen.open(arg);
            case "screenshot" -> Screenshot.grab(mc.gameDirectory, arg + ".png", mc.getMainRenderTarget(),
                    message -> CoreQuests.LOG.info("QA screenshot: {}", message.getString()));
            case "edit" -> Services.PLATFORM.sendToServer(new Payloads.SetEditMode(arg.equals("on")));
            case "team" -> mc.setScreen(new TeamScreen(mc.screen));
            case "tables" -> mc.setScreen(new RewardTablesScreen(mc.screen));
            case "editor" -> {
                QuestObject object = ClientQuests.book() == null ? null : ClientQuests.book().get(arg);
                if (object != null) {
                    mc.setScreen(new PropertyEditorScreen(mc.screen, object));
                }
            }
            case "filter" -> mc.setScreen(FilterEditorScreen.forExpression(mc.screen, arg, s -> { }));
            case "command" -> {
                String command = arg.replace('+', ' ');
                mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
            }
            case "pin" -> Services.PLATFORM.sendToServer(new Payloads.PinQuest(arg, true));
            case "vein" -> dev.nightbeam.corerpg.quests.client.vein.VeinClient.qaHeld = arg.equals("on");
            case "vein_shape" -> dev.nightbeam.corerpg.quests.client.vein.VeinClient.setShapeForQa(Integer.parseInt(arg));
            case "hud" -> mc.options.hideGui = arg.equals("off");
            case "look" -> {
                String[] parts = arg.split(",");
                mc.player.setYRot(Float.parseFloat(parts[0]));
                mc.player.setXRot(Float.parseFloat(parts[1]));
            }
            case "close" -> mc.setScreen(null);
            case "quit" -> mc.stop();
            default -> CoreQuests.LOG.warn("Unknown QA step {}", step);
        }
        if (!name.equals("wait")) {
            wait = Math.max(wait, 2);
        }
    }
}
