package dev.nightbeam.corerpg.quests.neoforge;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.gametest.QuestGameTests;
import dev.nightbeam.corerpg.quests.network.Packets;
import dev.nightbeam.corerpg.quests.registry.RegistryHelper;
import dev.nightbeam.corerpg.quests.server.CommonEvents;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Mod(CoreQuests.MOD_ID)
public final class CoreQuestsNeoForge {
    private final Map<Registry<?>, DeferredRegister<?>> registers = new HashMap<>();

    public CoreQuestsNeoForge(IEventBus modBus, ModContainer container, Dist dist) {
        CoreQuests.init(new RegistryHelper() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory) {
                DeferredRegister<R> register = (DeferredRegister<R>) registers.computeIfAbsent(registry, r -> {
                    DeferredRegister<R> created = DeferredRegister.create(registry.key(), CoreQuests.MOD_ID);
                    created.register(modBus);
                    return created;
                });
                return register.register(name, factory);
            }
        });
        modBus.addListener(this::registerPayloads);
        modBus.addListener((RegisterGameTestsEvent event) -> event.register(QuestGameTests.class));

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerStartingEvent event) -> CommonEvents.serverStarting(event.getServer()));
        bus.addListener((ServerStoppedEvent event) -> CommonEvents.serverStopped());
        bus.addListener((ServerTickEvent.Post event) -> CommonEvents.serverTickEnd(event.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.playerJoin(player);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CommonEvents.playerLeave(player);
            }
        });
        bus.addListener((RegisterCommandsEvent event) -> CommonEvents.registerCommands(event.getDispatcher(), event.getBuildContext()));
        bus.addListener(EventPriority.LOW, (ServerChatEvent event) -> {
            if (CommonEvents.redirectChat(event.getPlayer(), event.getRawText())) {
                event.setCanceled(true);
            }
        });

        if (dist.isClient()) {
            CoreQuestsNeoForgeClient.init(modBus);
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        for (Packets.ToClient<?> packet : Packets.toClient()) {
            toClient(registrar, packet);
        }
        for (Packets.ToServer<?> packet : Packets.toServer()) {
            toServer(registrar, packet);
        }
    }

    private static <T extends CustomPacketPayload> void toClient(PayloadRegistrar registrar, Packets.ToClient<T> packet) {
        registrar.playToClient(packet.type(), packet.codec(), (payload, context) -> context.enqueueWork(() -> packet.handler().accept(payload)));
    }

    private static <T extends CustomPacketPayload> void toServer(PayloadRegistrar registrar, Packets.ToServer<T> packet) {
        registrar.playToServer(packet.type(), packet.codec(), (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                packet.handler().accept(payload, player);
            }
        }));
    }
}
