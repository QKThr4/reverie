package com.liminalreverie.server;

import com.liminalreverie.Reverie;
import com.liminalreverie.net.EventPayload;
import com.liminalreverie.registry.ModSounds;
import com.liminalreverie.story.DreamEvents;
import com.liminalreverie.story.StoryData;
import com.liminalreverie.world.AreaDef;
import com.liminalreverie.world.Areas;
import com.liminalreverie.world.BuildManager;
import com.liminalreverie.world.HotelArea;
import com.liminalreverie.world.NpcSpawner;
import com.liminalreverie.world.Transit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs once per second per player (never per tick). Handles ambience, dream events, character spawning,
 * the "wrong door" omens in the normal world, and a safety net for falling out of the dream.
 */
public final class ReverieTicker {
    private static final class PS {
        int ambientCd = 4;
        int eventCd = 90;
        int npcCd = 2;
        long musicUntil = 0L;
    }

    private static final Map<UUID, PS> STATE = new HashMap<>();

    private ReverieTicker() {}

    public static void clear() {
        STATE.clear();
    }

    public static void swapMusic(ServerPlayer p, int ticks) {
        PS ps = STATE.computeIfAbsent(p.getUUID(), k -> new PS());
        ps.musicUntil = p.serverLevel().getGameTime() + ticks;
    }

    public static void second(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        if (Reverie.in(l)) {
            inReverie(p, l);
        } else if (l.dimension() == Level.OVERWORLD) {
            inOverworld(p, l);
        }
    }

    // ---------------------------------------------------------------- the Reverie

    private static void inReverie(ServerPlayer p, ServerLevel l) {
        AreaDef area = Areas.at(p.blockPosition());
        if (area == null) return;
        PS ps = STATE.computeIfAbsent(p.getUUID(), k -> new PS());
        RandomSource rnd = l.random;

        if (p.getY() < 20.0) { // fell out of the dream: put the player back at the area's start
            Transit.hop(p, area, area.sx, area.sy, area.sz, area.yaw);
            return;
        }
        if (BuildManager.isBuilding(area.id)) return;

        if (--ps.ambientCd <= 0) {
            ps.ambientCd = 7 + rnd.nextInt(8);
            boolean swapped = l.getGameTime() < ps.musicUntil;
            SoundEvent s = swapped ? ModSounds.CHIME.get() : area.ambient.get();
            l.playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.AMBIENT, swapped ? 0.25F : 0.7F, swapped ? 0.5F : 1.0F);
        }

        if (--ps.npcCd <= 0) {
            ps.npcCd = 5;
            NpcSpawner.ensure(p.server, area);
        }

        if (area == HotelArea.DEF) {
            BlockPos rel = p.blockPosition().offset(-area.ox, -area.oy, -area.oz);
            if (HotelArea.inFloatRoom(rel)) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 1, false, false, false));
            }
        }

        if (!area.safe && --ps.eventCd <= 0) {
            ps.eventCd = 100 + rnd.nextInt(100);
            List<DreamEvents> pool = DreamEvents.pool(rnd.nextInt(100) < 12);
            pool.get(rnd.nextInt(pool.size())).trigger(p);
        }
    }

    // ---------------------------------------------------------------- the normal world (Chapter 1)

    private static void inOverworld(ServerPlayer p, ServerLevel l) {
        StoryData d = StoryData.get(p.server);
        if (d.chapter != 1 || d.hasWrongDoor) return;
        d.overworldSeconds++;
        if (d.overworldSeconds % 30 == 0) d.setDirty();
        long t = d.overworldSeconds;
        if (t == 300) {
            PacketDistributor.sendToPlayer(p, new EventPayload("fog_shift", 400));
            l.playSound(null, p.getX() + 18, p.getY(), p.getZ(), ModSounds.BELL.get(), SoundSource.AMBIENT, 0.7F, 0.7F);
        } else if (t == 480) {
            l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.AMB_LOBBY.get(), SoundSource.AMBIENT, 0.5F, 0.8F);
        } else if (t >= 600 && t % 20 == 0) {
            placeWrongDoor(p, l, d);
        }
    }

    /** An ordinary wooden door, standing somewhere it shouldn't. Placed once, 22-36 blocks from the player. */
    private static void placeWrongDoor(ServerPlayer p, ServerLevel l, StoryData d) {
        RandomSource r = l.random;
        for (int i = 0; i < 12; i++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            int dist = 22 + r.nextInt(14);
            int x = Mth.floor(p.getX() + Math.cos(ang) * dist);
            int z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
            if (!l.hasChunkAt(new BlockPos(x, 64, z))) continue;
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState below = l.getBlockState(pos.below());
            if (!l.getBlockState(pos).isAir() || !l.getBlockState(pos.above()).isAir()) continue;
            if (!below.isFaceSturdy(l, pos.below(), Direction.UP) || !below.getFluidState().isEmpty()) continue;
            Direction face = Direction.getNearest(p.getX() - x, 0.0, p.getZ() - z);
            BlockState lo = Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(DoorBlock.FACING, face).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            l.setBlock(pos, lo, 3);
            l.setBlock(pos.above(), lo.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
            d.hasWrongDoor = true;
            d.wrongDoorPos = pos.asLong();
            d.setDirty();
            return;
        }
    }
}
