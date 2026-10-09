package com.liminalreverie.world;

import com.liminalreverie.Reverie;
import com.liminalreverie.registry.ModSounds;
import com.liminalreverie.story.StoryData;
import com.liminalreverie.story.StoryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;

/** All movement between areas goes through here: gates, lazy building, soft fades and phase changes. */
public final class Transit {
    private Transit() {}

    /** Normal travel: respects the area's gate and builds the area first if needed. */
    public static void enter(ServerPlayer p, AreaDef dest) {
        MinecraftServer s = p.server;
        if (dest == null) return;
        if (Reverie.level(s) == null) {
            p.sendSystemMessage(Component.literal("The Reverie dimension is not available."));
            return;
        }
        StoryData d = StoryData.get(s);
        if (!dest.gate.test(d)) {
            p.displayClientMessage(Component.translatable("lr.msg.locked." + dest.id).withStyle(ChatFormatting.GRAY), true);
            p.level().playSound(null, p.blockPosition(), ModSounds.WRONG.get(), SoundSource.PLAYERS, 0.5F, 0.7F);
            return;
        }
        go(p, dest);
    }

    /** Ignores gates (commands, endings). */
    public static void force(ServerPlayer p, AreaDef dest) {
        if (dest == null || Reverie.level(p.server) == null) return;
        go(p, dest);
    }

    private static void go(ServerPlayer p, AreaDef dest) {
        StoryData d = StoryData.get(p.server);
        if (!d.areasBuilt.contains(dest.id)) {
            BuildManager.request(p, dest);
        } else {
            arrive(p, dest);
        }
    }

    public static void arrive(ServerPlayer p, AreaDef dest) {
        MinecraftServer s = p.server;
        ServerLevel rl = Reverie.level(s);
        if (rl == null) return;
        AreaDef prev = Reverie.in(p.level()) ? Areas.at(p.blockPosition()) : null;
        if (prev != null && prev != dest) Phases.onLeave(s, prev);
        p.removeEffect(MobEffects.DARKNESS);
        p.teleportTo(rl, dest.ox + dest.sx, dest.oy + dest.sy, dest.oz + dest.sz, dest.yaw, 0.0F);
        p.fallDistance = 0.0F;
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, false));
        StoryManager.onArrive(p, dest);
    }

    /** Teleport inside the Reverie to a position relative to {@code def}'s origin. */
    public static void hop(ServerPlayer p, AreaDef def, double x, double y, double z, float yaw) {
        ServerLevel rl = Reverie.level(p.server);
        if (rl == null) return;
        p.teleportTo(rl, def.ox + x, def.oy + y, def.oz + z, yaw, 0.0F);
        p.fallDistance = 0.0F;
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0, false, false, false));
    }

    public static void toOverworld(ServerPlayer p) {
        ServerLevel ow = p.server.overworld();
        BlockPos s = ow.getSharedSpawnPos();
        int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING, s.getX(), s.getZ());
        p.removeEffect(MobEffects.DARKNESS);
        p.teleportTo(ow, s.getX() + 0.5, y, s.getZ() + 0.5, p.getYRot(), 0.0F);
        p.fallDistance = 0.0F;
    }
}
