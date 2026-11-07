package dev.nightbeam.corerpg.quests.reward.types;

import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardContext;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Runs a command as the claiming player. Placeholders: {p} player name, {uuid}, {x} {y} {z} position, {quest} quest id,
 * {team} the player's team id. The default permission level (2) lets it run gameplay commands without making the
 * player an operator.
 */
public class CommandReward extends Reward {
    public String command = "/say Hello {p}!";
    public int permissionLevel = 2;
    public boolean silent = true;
    public String feedbackMessage = "";

    @Override
    public RewardType<?> type() {
        return RewardTypes.COMMAND;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("reward");
        p.string("command", "/say Hello {p}!", () -> command, v -> command = v);
        p.integer("permission_level", 2, 0, 4, () -> permissionLevel, v -> permissionLevel = v);
        p.bool("silent", true, () -> silent, v -> silent = v);
        p.string("feedback_message", "", () -> feedbackMessage, v -> feedbackMessage = v);
    }

    @Override
    public void claim(RewardContext ctx) {
        ServerPlayer player = ctx.player();
        String cmd = expand(command, player);
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        CommandSourceStack source = player.createCommandSourceStack().withPermission(permissionLevel);
        if (silent) {
            source = source.withSuppressedOutput();
        }
        player.server.getCommands().performPrefixedCommand(source, cmd);
        if (!feedbackMessage.isBlank() && ctx.shouldNotify()) {
            ctx.announce(TextUtil.format(expand(feedbackMessage, player)));
        }
    }

    private String expand(String text, ServerPlayer player) {
        return text.replace("{p}", player.getGameProfile().getName())
                .replace("{uuid}", player.getStringUUID())
                .replace("{x}", String.valueOf(player.getBlockX()))
                .replace("{y}", String.valueOf(player.getBlockY()))
                .replace("{z}", String.valueOf(player.getBlockZ()))
                .replace("{quest}", quest == null ? "" : quest.id());
    }

    @Override
    protected Component defaultTitle() {
        return feedbackMessage.isBlank() ? Component.translatable("corerpg_quests.reward_type.corerpg_quests.command")
                : TextUtil.format(feedbackMessage);
    }

    @Override
    public void addTooltip(List<Component> lines) {
        lines.add(Component.literal(command).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
