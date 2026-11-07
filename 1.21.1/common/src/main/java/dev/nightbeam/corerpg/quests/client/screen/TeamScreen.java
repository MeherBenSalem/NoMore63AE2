package dev.nightbeam.corerpg.quests.client.screen;

import dev.nightbeam.corerpg.quests.client.ClientQuests;
import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.client.screen.team.Chip;
import dev.nightbeam.corerpg.quests.client.screen.team.Hit;
import dev.nightbeam.corerpg.quests.client.widget.ThemedButton;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.platform.Services;
import dev.nightbeam.corerpg.quests.team.Team;
import dev.nightbeam.corerpg.quests.team.TeamRank;
import dev.nightbeam.corerpg.quests.team.TeamType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The team screen: who is in your team and what you may do with them, party creation and invitations, and team
 * settings. Every action goes to the server as a {@link Payloads.TeamAction}, which runs the same rules as /team,
 * so this screen only decides which buttons to offer.
 */
public class TeamScreen extends Screen {
    private enum Mode {
        MAIN, PLAYERS, SETTINGS
    }

    private record Confirm(Component message, String action, String arg) {
    }

    private record Row(@Nullable UUID player, Component label, boolean header) {
    }

    private record Action(Component label, int accent, Runnable run) {
    }

    private static final int[] PALETTE = {
            0xE8743B, 0xE8C15A, 0xF2E28C, 0x8FC27A, 0x4FAF8C, 0x6FB6E0, 0x4C7FD6, 0xB58CE6,
            0xD96BB0, 0xD9534F, 0xA0522D, 0xC9B79C, 0xF5F5F5, 0x9C8E7E, 0x5E6B73, 0x2E3A3F
    };
    private static final int ROW = 13;
    private static final int SWATCH = 13;

    private final @Nullable Screen parent;
    private Mode mode = Mode.MAIN;
    private int lastVersion = -1;
    private @Nullable UUID selected;
    private @Nullable Confirm confirm;
    private final List<Hit> hits = new ArrayList<>();

    private String createName = "";
    private String draftName = "";
    private String draftDescription = "";
    private String draftHex = "";
    private boolean draftFree;

    private double listScroll;
    private double sideScroll;
    private int listContent;
    private int sideContent;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int bodyY;
    private int bodyBottom;
    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int sideListY;

    private @Nullable ThemedButton createButton;
    private @Nullable ThemedButton saveButton;

    public TeamScreen(@Nullable Screen parent) {
        super(Component.translatable("corerpg_quests.gui.team.title"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ Helpers

    private static void send(String action, String arg) {
        Services.PLATFORM.sendToServer(new Payloads.TeamAction(action, arg));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("corerpg_quests.gui.team." + key, args);
    }

    private static Component rankLabel(TeamRank rank) {
        return t("rank." + rank.getSerializedName()).copy().withStyle(rank.color);
    }

    private static int rankColor(TeamRank rank) {
        Integer color = rank.color.getColor();
        return color == null ? Theme.TEXT : color | 0xFF000000;
    }

    private static Set<UUID> onlineIds() {
        Set<UUID> set = new HashSet<>();
        ClientQuests.online().forEach(p -> set.add(p.id()));
        return set;
    }

    private static TeamRank selfRank(Team team) {
        return team.rank(ClientQuests.self());
    }

    private static boolean isParty(Team team) {
        return team.type() == TeamType.PARTY;
    }

    private static boolean validName(String name) {
        String trimmed = name.trim();
        return trimmed.length() >= 3 && trimmed.length() <= 32;
    }

    // ------------------------------------------------------------------ Layout and widgets

    @Override
    protected void init() {
        lastVersion = ClientQuests.version();
        createButton = null;
        saveButton = null;
        pw = Math.min(width - 16, 460);
        ph = Math.min(height - 16, 290);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        Team team = ClientQuests.team();
        int header = 26;
        if (team != null && !team.description().isBlank()) {
            header += 10;
        }
        if (team != null && !team.stages().isEmpty()) {
            header += 14;
        }
        bodyY = py + header + 4;
        bodyBottom = py + ph - 30;
        leftX = px + 8;
        leftW = (int) ((pw - 22) * 0.52);
        rightX = leftX + leftW + 6;
        rightW = px + pw - 8 - rightX;
        sideListY = bodyY;

        if (selected != null && (team == null || team.rank(selected) == TeamRank.NONE)) {
            selected = null;
        }
        if (team == null && mode != Mode.MAIN) {
            mode = Mode.MAIN;
        }

        if (confirm != null) {
            int cw = Math.min(240, width - 20);
            int cx = (width - cw) / 2;
            int cy = height / 2 + 10;
            addRenderableWidget(new ThemedButton(cx + 10, cy, cw / 2 - 15, 18, t("confirm"), b -> {
                Confirm c = confirm;
                confirm = null;
                if (c != null) {
                    send(c.action(), c.arg());
                    selected = null;
                }
                rebuildWidgets();
            }, Theme.CRIMSON));
            addRenderableWidget(new ThemedButton(cx + cw / 2 + 5, cy, cw / 2 - 15, 18, t("cancel"), b -> {
                confirm = null;
                rebuildWidgets();
            }, Theme.MUTED));
            return;
        }

        int bottomY = py + ph - 24;
        int done = 64;
        addRenderableWidget(new ThemedButton(px + pw - 8 - done, bottomY, done, 18, mode == Mode.MAIN ? t("done") : t("back"), b -> {
            if (mode == Mode.MAIN) {
                onClose();
            } else {
                mode = Mode.MAIN;
                sideScroll = 0;
                rebuildWidgets();
            }
        }, Theme.MUTED));

        if (team == null) {
            return;
        }
        switch (mode) {
            case MAIN -> initMain(team, bottomY);
            case SETTINGS -> initSettings(team, bottomY);
            case PLAYERS -> {
            }
        }
    }

    private void initMain(Team team, int bottomY) {
        TeamRank rank = selfRank(team);
        int x = leftX;
        if (team.type() != TeamType.PLAYER) {
            addRenderableWidget(new ThemedButton(x, bottomY, 100, 18, ClientQuests.teamChat() ? t("chat_on") : t("chat_off"),
                    b -> send("toggle_chat", ""), Theme.SKY));
            x += 104;
            addRenderableWidget(new ThemedButton(x, bottomY, 56, 18, t("leave"), b -> {
                confirm = new Confirm(t("confirm.leave", team.displayName()), "leave", "");
                rebuildWidgets();
            }, Theme.CRIMSON));
            x += 60;
            if (isParty(team) && team.owner().equals(ClientQuests.self())) {
                addRenderableWidget(new ThemedButton(x, bottomY, 62, 18, t("disband"), b -> {
                    confirm = new Confirm(t("confirm.disband", team.displayName()), "disband", "");
                    rebuildWidgets();
                }, Theme.CRIMSON));
            }
        }

        List<ThemedButton> side = new ArrayList<>();
        if (rank.isOfficerOrAbove() && team.type() != TeamType.SERVER) {
            side.add(new ThemedButton(0, 0, rightW, 18, isParty(team) ? t("invite_players") : t("find_players"), b -> {
                mode = Mode.PLAYERS;
                sideScroll = 0;
                rebuildWidgets();
            }, Theme.SKY));
            side.add(new ThemedButton(0, 0, rightW, 18, t("settings"), b -> {
                draftName = team.name();
                draftDescription = team.description();
                draftHex = String.format(Locale.ROOT, "%06X", team.color() & 0xFFFFFF);
                draftFree = team.freeToJoin();
                mode = Mode.SETTINGS;
                rebuildWidgets();
            }, Theme.GOLD));
        }
        int sy = bodyBottom - side.size() * 21 + 3;
        for (ThemedButton b : side) {
            b.setPosition(rightX, sy);
            addRenderableWidget(b);
            sy += 21;
        }

        if (team.type() == TeamType.PLAYER) {
            int y = bodyY + 24;
            int buttonW = 52;
            EditBox name = new EditBox(font, rightX + 1, y + 1, rightW - buttonW - 6, 16, t("create.hint"));
            name.setMaxLength(32);
            name.setHint(t("create.hint").copy().withStyle(s -> s.withColor(Theme.FAINT & 0xFFFFFF)));
            name.setValue(createName);
            name.setResponder(value -> {
                createName = value;
                if (createButton != null) {
                    createButton.active = validName(value);
                }
            });
            addRenderableWidget(name);
            createButton = new ThemedButton(rightX + rightW - buttonW, y, buttonW, 18, t("create.button"), b -> {
                if (validName(createName)) {
                    send("create", createName.trim());
                    createName = "";
                }
            }, Theme.SAGE);
            createButton.active = validName(createName);
            addRenderableWidget(createButton);
            sideListY = y + 26;
        }
    }

    /** Settings layout positions, shared by {@link #initSettings} and {@link #renderSettings}. */
    private int settingsFieldsY() {
        return bodyY + 24;
    }

    private int settingsHexY() {
        return bodyY + 24 + 2 * (SWATCH + 2) + 6;
    }

    private int settingsColumnWidth() {
        return (pw - 22) / 2;
    }

    private void initSettings(Team team, int bottomY) {
        int colW = settingsColumnWidth();
        int x = leftX;
        int y = settingsFieldsY();
        if (isParty(team)) {
            EditBox name = new EditBox(font, x + 1, y + 1, colW - 2, 16, t("settings.name"));
            name.setMaxLength(32);
            name.setValue(draftName);
            name.setResponder(v -> {
                draftName = v;
                updateSave(team);
            });
            addRenderableWidget(name);
            y += 34;
        }
        EditBox description = new EditBox(font, x + 1, y + 1, colW - 2, 16, t("settings.description"));
        description.setMaxLength(200);
        description.setValue(draftDescription);
        description.setResponder(v -> {
            draftDescription = v;
            updateSave(team);
        });
        addRenderableWidget(description);
        y += 26;
        if (isParty(team)) {
            addRenderableWidget(new ThemedButton(x, y, colW, 18, freeLabel(), b -> {
                draftFree = !draftFree;
                b.setMessage(freeLabel());
                updateSave(team);
            }, Theme.SKY));
        }

        int rx = leftX + colW + 6;
        EditBox hex = new EditBox(font, rx + 30, settingsHexY() + 1, 60, 16, t("settings.hex"));
        hex.setMaxLength(7);
        hex.setValue(draftHex);
        hex.setFilter(s -> s.matches("#?[0-9a-fA-F]{0,6}"));
        hex.setResponder(v -> {
            draftHex = (v.startsWith("#") ? v.substring(1) : v).toUpperCase(Locale.ROOT);
            updateSave(team);
        });
        addRenderableWidget(hex);

        saveButton = new ThemedButton(leftX, bottomY, 110, 18, t("settings.save"), b -> saveSettings(team), Theme.SAGE);
        addRenderableWidget(saveButton);
        updateSave(team);
    }

    private Component freeLabel() {
        return t("settings.free", draftFree ? t("yes") : t("no"));
    }

    private @Nullable Integer parsedColor() {
        if (draftHex.length() != 6) {
            return null;
        }
        try {
            return Integer.parseUnsignedInt(draftHex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean settingsChanged(Team team) {
        Integer color = parsedColor();
        return isParty(team) && !draftName.trim().equals(team.name())
                || !draftDescription.equals(team.description())
                || color != null && color != (team.color() & 0xFFFFFF)
                || isParty(team) && draftFree != team.freeToJoin();
    }

    private void updateSave(Team team) {
        if (saveButton != null) {
            boolean nameOk = !isParty(team) || validName(draftName);
            saveButton.active = nameOk && parsedColor() != null && settingsChanged(team);
        }
    }

    private void saveSettings(Team team) {
        if (isParty(team) && !draftName.trim().equals(team.name()) && validName(draftName)) {
            send("set_name", draftName.trim());
        }
        if (!draftDescription.equals(team.description())) {
            send("set_description", draftDescription);
        }
        Integer color = parsedColor();
        if (color != null && color != (team.color() & 0xFFFFFF)) {
            send("set_color", String.format(Locale.ROOT, "%06X", color));
        }
        if (isParty(team) && draftFree != team.freeToJoin()) {
            send("set_free_to_join", String.valueOf(draftFree));
        }
        mode = Mode.MAIN;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        if (ClientQuests.version() != lastVersion) {
            rebuildWidgets();
        }
    }

    // ------------------------------------------------------------------ Rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Team team = ClientQuests.team();
        int accent = team == null ? Theme.EMBER : team.color() | 0xFF000000;
        Theme.panel(g, px, py, pw, ph, accent);
        if (team == null) {
            g.drawCenteredString(font, t("loading"), width / 2, py + ph / 2 - 4, Theme.MUTED);
            return;
        }
        int mx = confirm != null ? -1 : mouseX;
        int my = confirm != null ? -1 : mouseY;
        renderHeader(g, team);
        switch (mode) {
            case MAIN -> {
                renderMembers(g, team, mx, my);
                renderSide(g, team, mx, my);
            }
            case PLAYERS -> {
                renderMembers(g, team, mx, my);
                renderPlayers(g, team, mx, my);
            }
            case SETTINGS -> renderSettings(g, team, mx, my);
        }
        if (confirm != null) {
            renderConfirm(g, confirm);
        }
    }

    private void renderHeader(GuiGraphics g, Team team) {
        int x = px + 10;
        int y = py + 8;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.25f, 1.25f, 1);
        MutableComponent name = team.displayName();
        g.drawString(font, name, 0, 0, team.color() | 0xFF000000, true);
        int nameW = (int) (font.width(name) * 1.25f);
        g.pose().popPose();
        g.drawString(font, t("type." + team.type().getSerializedName()), x + nameW + 8, y + 3, Theme.MUTED, false);
        String id = "#" + team.shortId();
        g.drawString(font, id, px + pw - 10 - font.width(id), y + 3, Theme.FAINT, false);
        y += 16;
        if (!team.description().isBlank()) {
            String desc = font.plainSubstrByWidth(team.description(), pw - 20);
            g.drawString(font, Component.literal(desc).withStyle(s -> s.withItalic(true)), x, y, Theme.MUTED, false);
            y += 10;
        }
        if (!team.stages().isEmpty()) {
            Component label = t("stages");
            g.drawString(font, label, x, y + 2, Theme.FAINT, false);
            int cx = x + font.width(label) + 4;
            int limit = px + pw - 10;
            List<String> stages = new ArrayList<>(team.stages());
            for (int i = 0; i < stages.size(); i++) {
                Component chip = Component.literal(stages.get(i));
                int w = Chip.width(font, chip);
                if (cx + w > limit - 24 && i < stages.size() - 1) {
                    Chip.draw(g, font, Component.literal("+" + (stages.size() - i)), cx, y, Theme.MUTED, false);
                    break;
                }
                Chip.draw(g, font, chip, cx, y, Theme.VIOLET, false);
                cx += w + 3;
            }
        }
        Theme.separator(g, px + 8, bodyY - 3, pw - 16, Theme.withAlpha(team.color(), 0x90));
    }

    private static List<Row> memberRows(Team team) {
        List<Row> rows = new ArrayList<>();
        List<Map.Entry<UUID, TeamRank>> entries = new ArrayList<>(team.ranks().entrySet());
        entries.sort(Comparator.<Map.Entry<UUID, TeamRank>>comparingInt(e -> -e.getValue().power)
                .thenComparing(e -> team.playerName(e.getKey()).toLowerCase(Locale.ROOT)));
        List<Map.Entry<UUID, TeamRank>> members = entries.stream().filter(e -> e.getValue().isMember()).toList();
        List<Map.Entry<UUID, TeamRank>> allies = entries.stream().filter(e -> e.getValue() == TeamRank.ALLY).toList();
        List<Map.Entry<UUID, TeamRank>> invited = entries.stream().filter(e -> e.getValue() == TeamRank.INVITED).toList();
        rows.add(new Row(null, t("members", members.size()), true));
        members.forEach(e -> rows.add(new Row(e.getKey(), Component.literal(team.playerName(e.getKey())), false)));
        if (!allies.isEmpty()) {
            rows.add(new Row(null, t("allies", allies.size()), true));
            allies.forEach(e -> rows.add(new Row(e.getKey(), Component.literal(team.playerName(e.getKey())), false)));
        }
        if (!invited.isEmpty()) {
            rows.add(new Row(null, t("invited", invited.size()), true));
            invited.forEach(e -> rows.add(new Row(e.getKey(), Component.literal(team.playerName(e.getKey())), false)));
        }
        return rows;
    }

    private void renderMembers(GuiGraphics g, Team team, int mouseX, int mouseY) {
        g.fill(leftX, bodyY, leftX + leftW, bodyBottom, 0x40000000);
        Theme.outline(g, leftX, bodyY, leftW, bodyBottom - bodyY, Theme.LINE_SOFT);
        List<Row> rows = memberRows(team);
        Set<UUID> online = onlineIds();
        listContent = rows.size() * ROW + 6;
        listScroll = clampScroll(listScroll, listContent, bodyBottom - bodyY);
        g.enableScissor(leftX + 1, bodyY + 1, leftX + leftW - 1, bodyBottom - 1);
        int y = bodyY + 3 - (int) listScroll;
        for (Row row : rows) {
            if (y + ROW >= bodyY && y <= bodyBottom) {
                if (row.header()) {
                    g.drawString(font, row.label(), leftX + 5, y + 3, Theme.GOLD, false);
                } else {
                    renderMemberRow(g, team, row, y, online, mouseX, mouseY);
                }
            }
            y += ROW;
        }
        g.disableScissor();
        scrollbar(g, leftX + leftW - 3, bodyY + 1, bodyBottom - bodyY - 2, listScroll, listContent);
    }

    private void renderMemberRow(GuiGraphics g, Team team, Row row, int y, Set<UUID> online, int mouseX, int mouseY) {
        UUID id = row.player();
        int top = Math.max(y, bodyY);
        int bottom = Math.min(y + ROW, bodyBottom);
        boolean hovered = mode == Mode.MAIN && mouseX >= leftX && mouseX < leftX + leftW && mouseY >= top && mouseY < bottom;
        if (id.equals(selected)) {
            g.fill(leftX + 2, y, leftX + leftW - 2, y + ROW, Theme.withAlpha(team.color(), 0x40));
            g.fill(leftX + 2, y, leftX + 3, y + ROW, team.color() | 0xFF000000);
        } else if (hovered) {
            g.fill(leftX + 2, y, leftX + leftW - 2, y + ROW, 0x22FFFFFF);
        }
        boolean isOnline = online.contains(id);
        g.fill(leftX + 7, y + 5, leftX + 10, y + 8, isOnline ? Theme.SAGE : Theme.FAINT);
        TeamRank rank = team.rank(id);
        Component badge = rankLabel(rank);
        int badgeW = font.width(badge);
        String name = row.label().getString();
        if (id.equals(ClientQuests.self())) {
            name += " " + t("you").getString();
        }
        g.drawString(font, font.plainSubstrByWidth(name, leftW - badgeW - 24), leftX + 14, y + 3, isOnline ? Theme.TEXT : Theme.MUTED, false);
        g.drawString(font, badge, leftX + leftW - 6 - badgeW, y + 3, rankColor(rank), false);
        if (mode == Mode.MAIN && bottom > top) {
            hits.add(new Hit(leftX, top, leftW, bottom - top, () -> {
                selected = id.equals(selected) ? null : id;
                click();
            }));
        }
    }

    private void renderSide(GuiGraphics g, Team team, int mouseX, int mouseY) {
        if (team.type() == TeamType.PLAYER) {
            renderNoParty(g, team, mouseX, mouseY);
            return;
        }
        int y = bodyY + 2;
        UUID target = selected;
        if (target == null) {
            for (FormattedCharSequence line : font.split(t("select_hint"), rightW)) {
                g.drawString(font, line, rightX, y, Theme.MUTED, false);
                y += 10;
            }
            return;
        }
        TeamRank targetRank = team.rank(target);
        boolean online = onlineIds().contains(target);
        int cardH = 48;
        g.fill(rightX, y, rightX + rightW, y + cardH, Theme.BG_RAISED);
        Theme.outline(g, rightX, y, rightW, cardH, Theme.LINE);
        g.fill(rightX, y, rightX + 2, y + cardH, team.color() | 0xFF000000);
        g.drawString(font, font.plainSubstrByWidth(team.playerName(target), rightW - 10), rightX + 6, y + 4, Theme.TITLE, false);
        Component status = (online ? t("online") : t("offline")).copy().withStyle(s -> s.withColor((online ? Theme.SAGE : Theme.FAINT) & 0xFFFFFF));
        g.drawString(font, Component.empty().append(rankLabel(targetRank))
                .append(Component.literal(" · ").withStyle(s -> s.withColor(Theme.FAINT & 0xFFFFFF))).append(status), rightX + 6, y + 15, Theme.MUTED, false);

        List<Action> actions = memberActions(team, target, targetRank);
        int cx = rightX + 6;
        int cy = y + 30;
        if (actions.isEmpty()) {
            g.drawString(font, t("no_actions"), cx, cy + 1, Theme.FAINT, false);
        }
        for (Action action : actions) {
            int w = Chip.width(font, action.label());
            if (cx + w > rightX + rightW - 4 && cx > rightX + 6) {
                cx = rightX + 6;
                cy += Chip.HEIGHT + 3;
            }
            boolean hovered = mouseX >= cx && mouseX < cx + w && mouseY >= cy && mouseY < cy + Chip.HEIGHT;
            Chip.draw(g, font, action.label(), cx, cy, action.accent(), hovered);
            Runnable run = action.run();
            hits.add(new Hit(cx, cy, w, Chip.HEIGHT, () -> {
                click();
                run.run();
            }));
            cx += w + 3;
        }
    }

    /** The actions the local player may take on a team member, mirroring the server's rank rules. */
    private List<Action> memberActions(Team team, UUID target, TeamRank targetRank) {
        List<Action> actions = new ArrayList<>();
        if (target.equals(ClientQuests.self())) {
            return actions;
        }
        TeamRank self = selfRank(team);
        boolean owner = team.owner().equals(ClientQuests.self());
        String name = team.playerName(target);
        if (isParty(team)) {
            if (owner && targetRank == TeamRank.MEMBER) {
                actions.add(new Action(t("promote"), Theme.GOLD, () -> send("promote", target.toString())));
            }
            if (owner && targetRank == TeamRank.OFFICER) {
                actions.add(new Action(t("demote"), Theme.MUTED, () -> send("demote", target.toString())));
            }
            if (owner && targetRank.isMember()) {
                actions.add(new Action(t("transfer"), Theme.GOLD, () -> {
                    confirm = new Confirm(t("confirm.transfer", name, team.displayName()), "transfer", target.toString());
                    rebuildWidgets();
                }));
            }
            boolean canKick = targetRank.isMember() && !team.owner().equals(target) && self.isOfficerOrAbove()
                    && (!targetRank.isOfficerOrAbove() || owner);
            if (canKick) {
                actions.add(new Action(t("kick"), Theme.CRIMSON, () -> {
                    confirm = new Confirm(t("confirm.kick", name), "kick", target.toString());
                    rebuildWidgets();
                }));
            }
            if (targetRank == TeamRank.INVITED && self.isOfficerOrAbove()) {
                actions.add(new Action(t("cancel_invite"), Theme.CRIMSON, () -> send("cancel_invite", target.toString())));
            }
        }
        if (targetRank == TeamRank.ALLY && self.isOfficerOrAbove()) {
            actions.add(new Action(t("remove_ally"), Theme.CRIMSON, () -> send("ally_remove", target.toString())));
        }
        return actions;
    }

    private void renderNoParty(GuiGraphics g, Team team, int mouseX, int mouseY) {
        int y = bodyY + 2;
        g.drawString(font, t("create.title"), rightX, y, Theme.GOLD, false);
        g.drawString(font, font.plainSubstrByWidth(t("create.info").getString(), rightW), rightX, y + 11, Theme.FAINT, false);

        int top = sideListY;
        int bottom = sideBottom(team);
        List<ClientQuests.Invite> invites = ClientQuests.invites();
        List<ClientQuests.OpenParty> open = ClientQuests.openParties();
        sideContent = 2 * 14 + Math.max(1, invites.size()) * 16 + Math.max(1, open.size()) * 16 + 8;
        sideScroll = clampScroll(sideScroll, sideContent, bottom - top);
        if (bottom <= top) {
            return;
        }
        g.enableScissor(rightX, top, rightX + rightW, bottom);
        int ry = top - (int) sideScroll;
        g.drawString(font, t("invites.title"), rightX, ry + 2, Theme.GOLD, false);
        ry += 14;
        if (invites.isEmpty()) {
            g.drawString(font, t("invites.none"), rightX + 4, ry + 3, Theme.FAINT, false);
            ry += 16;
        }
        for (ClientQuests.Invite invite : invites) {
            Component accept = t("accept");
            Component decline = t("decline");
            int aw = Chip.width(font, accept);
            int dw = Chip.width(font, decline);
            g.fill(rightX, ry, rightX + 3, ry + 13, invite.color() | 0xFF000000);
            g.drawString(font, font.plainSubstrByWidth(invite.name(), rightW - aw - dw - 14), rightX + 6, ry + 3, Theme.TEXT, false);
            int ax = rightX + rightW - aw - dw - 5;
            chip(g, accept, ax, ry + 1, Theme.SAGE, mouseX, mouseY, top, bottom, () -> send("accept", invite.id().toString()));
            chip(g, decline, ax + aw + 3, ry + 1, Theme.CRIMSON, mouseX, mouseY, top, bottom, () -> send("decline", invite.id().toString()));
            ry += 16;
        }
        ry += 4;
        g.drawString(font, t("open.title"), rightX, ry + 2, Theme.GOLD, false);
        ry += 14;
        if (open.isEmpty()) {
            g.drawString(font, t("open.none"), rightX + 4, ry + 3, Theme.FAINT, false);
        }
        for (ClientQuests.OpenParty party : open) {
            Component join = t("join");
            int jw = Chip.width(font, join);
            Component count = t("open.members", party.members());
            int countW = font.width(count);
            g.fill(rightX, ry, rightX + 3, ry + 13, party.color() | 0xFF000000);
            g.drawString(font, font.plainSubstrByWidth(party.name(), rightW - jw - countW - 18), rightX + 6, ry + 3, Theme.TEXT, false);
            g.drawString(font, count, rightX + rightW - jw - countW - 8, ry + 3, Theme.FAINT, false);
            chip(g, join, rightX + rightW - jw - 3, ry + 1, Theme.SKY, mouseX, mouseY, top, bottom, () -> send("join", party.id().toString()));
            ry += 16;
        }
        g.disableScissor();
        scrollbar(g, rightX + rightW - 2, top, bottom - top, sideScroll, sideContent);
    }

    /** Bottom of the scrolling side area, above the side buttons. */
    private int sideBottom(Team team) {
        int buttons = selfRank(team).isOfficerOrAbove() && team.type() != TeamType.SERVER ? 2 : 0;
        return bodyBottom - buttons * 21 - 2;
    }

    private void renderPlayers(GuiGraphics g, Team team, int mouseX, int mouseY) {
        int top = bodyY;
        int bottom = bodyBottom;
        g.drawString(font, t("online_players"), rightX, top + 2, Theme.GOLD, false);
        top += 14;
        List<ClientQuests.OnlinePlayer> players = new ArrayList<>();
        for (ClientQuests.OnlinePlayer p : ClientQuests.online()) {
            if (!p.id().equals(ClientQuests.self()) && team.rank(p.id()) == TeamRank.NONE) {
                players.add(p);
            }
        }
        players.sort(Comparator.comparing(p -> p.name().toLowerCase(Locale.ROOT)));
        boolean canInvite = isParty(team) && selfRank(team).isOfficerOrAbove();
        boolean canAlly = selfRank(team).isOfficerOrAbove();
        sideContent = Math.max(1, players.size()) * 16;
        sideScroll = clampScroll(sideScroll, sideContent, bottom - top);
        g.enableScissor(rightX, top, rightX + rightW, bottom);
        int ry = top - (int) sideScroll;
        if (players.isEmpty()) {
            for (FormattedCharSequence line : font.split(t("no_players"), rightW - 4)) {
                g.drawString(font, line, rightX + 4, ry + 3, Theme.FAINT, false);
                ry += 10;
            }
        }
        for (ClientQuests.OnlinePlayer p : players) {
            Component invite = t("invite");
            Component ally = t("ally");
            int iw = canInvite ? Chip.width(font, invite) + 3 : 0;
            int aw = canAlly ? Chip.width(font, ally) : 0;
            g.fill(rightX + 2, ry + 5, rightX + 5, ry + 8, Theme.SAGE);
            g.drawString(font, font.plainSubstrByWidth(p.name(), rightW - iw - aw - 16), rightX + 9, ry + 3, Theme.TEXT, false);
            int cx = rightX + rightW - iw - aw - 5;
            if (canInvite) {
                chip(g, invite, cx, ry + 1, Theme.SKY, mouseX, mouseY, top, bottom, () -> send("invite", p.name()));
                cx += iw;
            }
            if (canAlly) {
                chip(g, ally, cx, ry + 1, Theme.VIOLET, mouseX, mouseY, top, bottom, () -> send("ally_add", p.name()));
            }
            ry += 16;
        }
        g.disableScissor();
        scrollbar(g, rightX + rightW - 2, top, bottom - top, sideScroll, sideContent);
    }

    private void renderSettings(GuiGraphics g, Team team, int mouseX, int mouseY) {
        int colW = settingsColumnWidth();
        int x = leftX;
        g.drawString(font, t("settings.title"), x, bodyY, Theme.GOLD, false);
        int y = settingsFieldsY();
        if (isParty(team)) {
            g.drawString(font, t("settings.name"), x, y - 10, Theme.MUTED, false);
            if (!validName(draftName)) {
                g.drawString(font, t("settings.name_length"), x, y + 20, Theme.CRIMSON, false);
            }
            y += 34;
        }
        g.drawString(font, t("settings.description"), x, y - 10, Theme.MUTED, false);

        int rx = leftX + colW + 6;
        int ry = settingsFieldsY();
        g.drawString(font, t("settings.color"), rx, ry - 10, Theme.MUTED, false);
        Integer current = parsedColor();
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = rx + (i % 8) * (SWATCH + 3);
            int sy = ry + (i / 8) * (SWATCH + 2);
            int color = PALETTE[i];
            boolean hovered = mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH;
            g.fill(sx, sy, sx + SWATCH, sy + SWATCH, color | 0xFF000000);
            boolean chosen = current != null && current == color;
            Theme.outline(g, sx, sy, SWATCH, SWATCH, chosen ? Theme.TITLE : hovered ? Theme.TEXT : Theme.LINE);
            if (chosen) {
                Theme.outline(g, sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, Theme.withAlpha(Theme.TITLE, 0x80));
            }
            hits.add(new Hit(sx, sy, SWATCH, SWATCH, () -> {
                click();
                draftHex = String.format(Locale.ROOT, "%06X", color);
                rebuildWidgets();
            }));
        }
        int hexY = settingsHexY();
        g.drawString(font, t("settings.hex"), rx, hexY + 5, Theme.MUTED, false);
        g.fill(rx + 96, hexY + 1, rx + 112, hexY + 17, current == null ? Theme.CRIMSON : current | 0xFF000000);
        Theme.outline(g, rx + 96, hexY + 1, 16, 16, Theme.LINE);
        int previewY = hexY + 26;
        g.drawString(font, t("settings.preview"), rx, previewY, Theme.FAINT, false);
        String shownName = isParty(team) ? draftName : team.name();
        Component name = Component.literal(font.plainSubstrByWidth(shownName, colW))
                .withStyle(s -> s.withColor(TextColor.fromRgb(current == null ? 0xFFFFFF : current)));
        g.drawString(font, name, rx, previewY + 11, 0xFFFFFFFF, true);
        if (!draftDescription.isBlank()) {
            g.drawString(font, Component.literal(font.plainSubstrByWidth(draftDescription, colW)).withStyle(s -> s.withItalic(true)),
                    rx, previewY + 22, Theme.MUTED, false);
        }
    }

    private void renderConfirm(GuiGraphics g, Confirm c) {
        g.fill(0, 0, width, height, 0xB0000000);
        int cw = Math.min(240, width - 20);
        List<FormattedCharSequence> lines = font.split(c.message(), cw - 20);
        int textH = lines.size() * 10;
        int cx = (width - cw) / 2;
        int buttonsY = height / 2 + 10;
        int cy = buttonsY - textH - 16;
        Theme.panel(g, cx, cy, cw, buttonsY + 26 - cy, Theme.CRIMSON);
        int y = cy + 9;
        for (FormattedCharSequence line : lines) {
            g.drawCenteredString(font, line, width / 2, y, Theme.TEXT);
            y += 10;
        }
    }

    private void chip(GuiGraphics g, Component label, int x, int y, int accent, int mouseX, int mouseY, int top, int bottom, Runnable action) {
        int w = Chip.width(font, label);
        boolean visible = y + Chip.HEIGHT > top && y < bottom;
        int hy = Math.max(y, top);
        int hb = Math.min(y + Chip.HEIGHT, bottom);
        boolean hovered = visible && mouseX >= x && mouseX < x + w && mouseY >= hy && mouseY < hb;
        Chip.draw(g, font, label, x, y, accent, hovered);
        if (visible) {
            hits.add(new Hit(x, hy, w, hb - hy, () -> {
                click();
                action.run();
            }));
        }
    }

    private static double clampScroll(double scroll, int content, int visible) {
        return Math.max(0, Math.min(scroll, Math.max(0, content - visible)));
    }

    private static void scrollbar(GuiGraphics g, int x, int y, int h, double scroll, int content) {
        if (content <= h || h <= 0) {
            return;
        }
        int thumb = Math.max(12, h * h / content);
        int ty = y + (int) ((h - thumb) * (scroll / (content - h)));
        g.fill(x, y, x + 2, y + h, 0x30FFFFFF);
        g.fill(x, ty, x + 2, ty + thumb, Theme.withAlpha(Theme.GOLD, 0xC0));
    }

    // ------------------------------------------------------------------ Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (confirm != null) {
            return true;
        }
        if (button == 0) {
            for (Hit hit : List.copyOf(hits)) {
                if (hit.contains(mouseX, mouseY)) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (confirm != null) {
            return true;
        }
        if (mode != Mode.SETTINGS && mouseX >= leftX && mouseX < leftX + leftW && mouseY >= bodyY && mouseY < bodyBottom) {
            listScroll = clampScroll(listScroll - scrollY * 14, listContent, bodyBottom - bodyY);
            return true;
        }
        if (mode != Mode.SETTINGS && mouseX >= rightX && mouseX < rightX + rightW && mouseY >= bodyY && mouseY < bodyBottom) {
            sideScroll = Math.max(0, sideScroll - scrollY * 14);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && confirm != null) {
            confirm = null;
            rebuildWidgets();
            return true;
        }
        if (keyCode == 256 && mode != Mode.MAIN) {
            mode = Mode.MAIN;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
