package dev.nightbeam.corerpg.quests.server;

import com.mojang.authlib.GameProfile;
import dev.nightbeam.corerpg.quests.team.Team;
import dev.nightbeam.corerpg.quests.team.TeamException;
import dev.nightbeam.corerpg.quests.team.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/** Team actions shared by the /team command and the team screen, including the chat messages they produce. */
public final class TeamActions {
    private TeamActions() {
    }

    public static void run(ServerQuests sq, ServerPlayer player, String action, String arg) throws TeamException {
        TeamManager teams = sq.teams();
        UUID self = player.getUUID();
        String selfName = player.getGameProfile().getName();
        switch (action) {
            case "create" -> {
                Team party = teams.createParty(self, selfName, arg);
                player.sendSystemMessage(Component.translatable("corerpg_quests.team.created", party.displayName()).withStyle(ChatFormatting.GREEN));
            }
            case "invite" -> {
                Team party = requireParty(teams, self);
                ServerPlayer target = sq.server().getPlayerList().getPlayerByName(arg);
                if (target == null) {
                    throw new TeamException("corerpg_quests.team.error.player_not_found", arg);
                }
                teams.invite(party, self, target.getUUID(), target.getGameProfile().getName());
                tell(sq, party, Component.translatable("corerpg_quests.team.invite_sent", target.getDisplayName()));
                target.sendSystemMessage(inviteMessage(party, player));
            }
            case "cancel_invite" -> {
                Team party = requireParty(teams, self);
                teams.cancelInvite(party, self, uuid(arg));
            }
            case "accept", "join" -> {
                Team team = teams.find(arg);
                if (team == null) {
                    throw new TeamException("corerpg_quests.team.error.team_not_found", arg);
                }
                teams.join(team, self, selfName);
                tell(sq, team, Component.translatable("corerpg_quests.team.joined", player.getDisplayName(), team.displayName()));
            }
            case "decline" -> {
                Team team = teams.find(arg);
                if (team != null) {
                    teams.decline(team, self);
                    tell(sq, team, Component.translatable("corerpg_quests.team.declined", player.getDisplayName()));
                }
            }
            case "leave" -> {
                Team party = requireParty(teams, self);
                teams.leave(self, selfName);
                tell(sq, party, Component.translatable("corerpg_quests.team.left", player.getDisplayName(), party.displayName()));
                player.sendSystemMessage(Component.translatable("corerpg_quests.team.you_left", party.displayName()));
            }
            case "kick" -> {
                Team party = requireParty(teams, self);
                UUID target = resolve(sq, party, arg);
                String name = party.playerName(target);
                teams.kick(party, self, target);
                tell(sq, party, Component.translatable("corerpg_quests.team.kicked", name));
                ServerPlayer kicked = sq.server().getPlayerList().getPlayer(target);
                if (kicked != null) {
                    kicked.sendSystemMessage(Component.translatable("corerpg_quests.team.you_were_kicked", party.displayName()).withStyle(ChatFormatting.RED));
                }
            }
            case "promote" -> {
                Team party = requireParty(teams, self);
                UUID target = resolve(sq, party, arg);
                teams.promote(party, self, target);
                tell(sq, party, Component.translatable("corerpg_quests.team.promoted", party.playerName(target)));
            }
            case "demote" -> {
                Team party = requireParty(teams, self);
                UUID target = resolve(sq, party, arg);
                teams.demote(party, self, target);
                tell(sq, party, Component.translatable("corerpg_quests.team.demoted", party.playerName(target)));
            }
            case "transfer" -> {
                Team party = requireParty(teams, self);
                UUID target = resolve(sq, party, arg);
                teams.transferOwnership(party, self, target);
                tell(sq, party, Component.translatable("corerpg_quests.team.transferred", party.playerName(target)));
            }
            case "ally_add" -> {
                Team team = teams.activeTeam(self, selfName);
                GameProfile profile = profile(sq.server(), arg);
                teams.setAlly(team, self, profile.getId(), profile.getName(), true);
                tell(sq, team, Component.translatable("corerpg_quests.team.ally_added", profile.getName()));
            }
            case "ally_remove" -> {
                Team team = teams.activeTeam(self, selfName);
                UUID target = resolve(sq, team, arg);
                teams.setAlly(team, self, target, team.playerName(target), false);
                tell(sq, team, Component.translatable("corerpg_quests.team.ally_removed", team.playerName(target)));
            }
            case "disband" -> {
                Team party = requireParty(teams, self);
                tell(sq, party, Component.translatable("corerpg_quests.team.disbanded", party.displayName()).withStyle(ChatFormatting.RED));
                teams.disband(party, self);
            }
            case "set_name" -> teams.setName(requireParty(teams, self), self, arg);
            case "set_description" -> teams.setDescription(teams.activeTeam(self, selfName), self, arg);
            case "set_color" -> {
                try {
                    String hex = arg.startsWith("#") ? arg.substring(1) : arg;
                    teams.setColor(teams.activeTeam(self, selfName), self, Integer.parseUnsignedInt(hex, 16));
                } catch (NumberFormatException e) {
                    throw new TeamException("corerpg_quests.team.error.bad_color", arg);
                }
            }
            case "set_free_to_join" -> teams.setFreeToJoin(requireParty(teams, self), self, Boolean.parseBoolean(arg));
            case "toggle_chat" -> {
                requireParty(teams, self);
                boolean on = teams.toggleTeamChat(self);
                player.sendSystemMessage(Component.translatable(on ? "corerpg_quests.team.chat_on" : "corerpg_quests.team.chat_off"));
                sq.sendState(player);
            }
            default -> throw new TeamException("corerpg_quests.team.error.unknown_action", action);
        }
    }

    private static Team requireParty(TeamManager teams, UUID player) throws TeamException {
        Team party = teams.partyOf(player);
        if (party == null) {
            throw new TeamException("corerpg_quests.team.error.not_in_party");
        }
        return party;
    }

    private static UUID uuid(String text) throws TeamException {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new TeamException("corerpg_quests.team.error.player_not_found", text);
        }
    }

    /** A member/ally of the team by UUID or name. */
    private static UUID resolve(ServerQuests sq, Team team, String arg) throws TeamException {
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) {
        }
        for (UUID id : team.ranks().keySet()) {
            if (team.playerName(id).equalsIgnoreCase(arg)) {
                return id;
            }
        }
        ServerPlayer online = sq.server().getPlayerList().getPlayerByName(arg);
        if (online != null) {
            return online.getUUID();
        }
        throw new TeamException("corerpg_quests.team.error.player_not_found", arg);
    }

    private static GameProfile profile(MinecraftServer server, String name) throws TeamException {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getGameProfile();
        }
        Optional<GameProfile> cached = server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(name);
        return cached.orElseThrow(() -> new TeamException("corerpg_quests.team.error.player_not_found", name));
    }

    /** Sends a message to every online member of the team. */
    public static void tell(ServerQuests sq, Team team, Component message) {
        MutableComponent line = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(team.displayName())
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(message.copy().withStyle(s -> s.getColor() == null ? s.withColor(ChatFormatting.GRAY) : s));
        for (ServerPlayer member : team.onlineMembers(sq.server())) {
            member.sendSystemMessage(line);
        }
    }

    private static Component inviteMessage(Team party, ServerPlayer from) {
        MutableComponent accept = Component.translatable("corerpg_quests.team.accept").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team accept " + party.id()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("corerpg_quests.team.accept.hover"))));
        MutableComponent decline = Component.translatable("corerpg_quests.team.decline").withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team decline " + party.id())));
        return Component.translatable("corerpg_quests.team.invited", from.getDisplayName(), party.displayName())
                .append(" ").append(accept).append(" ").append(decline);
    }
}
