package dev.nightbeam.corerpg.quests.forge;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.network.Packets;
import dev.nightbeam.corerpg.quests.network.compat.CustomPacketPayload;
import dev.nightbeam.corerpg.quests.registry.RegistryHelper;
import dev.nightbeam.corerpg.quests.server.CommonEvents;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Mod(CoreQuests.MOD_ID)
public final class CoreQuestsForge {
    /**
     * Minecraft 1.20.1 edition: every payload travels in one envelope message on one channel; the envelope carries the
     * payload's id, then the payload written by its codec. A missing channel on the other side is accepted (like the
     * optional payloads on 1.21.1), so vanilla clients can still join.
     */
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(CoreQuests.id("main"), () -> "1",
            NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"));

    record Envelope(CustomPacketPayload payload) {
    }

    private final Map<Registry<?>, DeferredRegister<?>> registers = new HashMap<>();
    private static final Map<ResourceLocation, Packets.ToServer<?>> TO_SERVER = new HashMap<>();
    private static final Map<ResourceLocation, Packets.ToClient<?>> TO_CLIENT = new HashMap<>();

    public CoreQuestsForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
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
        registerPackets();

        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((ServerStartingEvent event) -> CommonEvents.serverStarting(event.getServer()));
        bus.addListener((ServerStoppedEvent event) -> CommonEvents.serverStopped());
        bus.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                CommonEvents.serverTickEnd(event.getServer());
            }
        });
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

        if (FMLEnvironment.dist.isClient()) {
            CoreQuestsForgeClient.init(modBus);
        }
    }

    private static void registerPackets() {
        for (Packets.ToServer<?> packet : Packets.toServer()) {
            TO_SERVER.put(packet.type().id(), packet);
        }
        for (Packets.ToClient<?> packet : Packets.toClient()) {
            TO_CLIENT.put(packet.type().id(), packet);
        }
        CHANNEL.registerMessage(0, Envelope.class, CoreQuestsForge::encode, CoreQuestsForge::decode, CoreQuestsForge::handle);
    }

    private static void encode(Envelope envelope, FriendlyByteBuf buf) {
        buf.writeResourceLocation(envelope.payload().type().id());
        Packets.write(envelope.payload(), buf);
    }

    private static Envelope decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        return new Envelope(Packets.read(id, buf));
    }

    private static void handle(Envelope envelope, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        CustomPacketPayload payload = envelope.payload();
        if (context.getDirection().getReceptionSide().isServer()) {
            Packets.ToServer<?> packet = TO_SERVER.get(payload.type().id());
            ServerPlayer sender = context.getSender();
            if (packet != null && sender != null) {
                context.enqueueWork(() -> acceptServer(packet, payload, sender));
            }
        } else {
            Packets.ToClient<?> packet = TO_CLIENT.get(payload.type().id());
            if (packet != null) {
                context.enqueueWork(() -> acceptClient(packet, payload));
            }
        }
        context.setPacketHandled(true);
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void acceptServer(Packets.ToServer<T> packet, CustomPacketPayload payload, ServerPlayer sender) {
        packet.handler().accept((T) payload, sender);
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void acceptClient(Packets.ToClient<T> packet, CustomPacketPayload payload) {
        packet.handler().accept((T) payload);
    }
}
