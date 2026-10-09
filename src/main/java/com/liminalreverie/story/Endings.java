package com.liminalreverie.story;

import com.liminalreverie.registry.ModBlocks;
import com.liminalreverie.world.Areas;
import com.liminalreverie.world.Transit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import com.liminalreverie.block.MemoryFragmentBlock;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The four endings: wake, stay, remember and the secret "window". No boss fight; each is a short sequence
 * of titles followed by a destination. Choosing one finishes the story but never locks the world.
 */
public final class Endings {
    private static final Map<String, Integer> LINES = Map.of("wake", 3, "stay", 3, "remember", 4, "window", 3);

    private static final Set<UUID> PLAYING = new HashSet<>();

    private Endings() {}

    public static boolean valid(String id) {
        return LINES.containsKey(id);
    }

    public static Iterable<String> ids() {
        return LINES.keySet();
    }

    public static void play(ServerPlayer sp, String id) {
        Integer n = LINES.get(id);
        if (n == null || !PLAYING.add(sp.getUUID())) return;
        sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 110 * n + 120, 0, false, false, false));
        for (int i = 0; i < n; i++) {
            final String key = "lr.end." + id + "." + (i + 1);
            Scheduler.later(40 + 110 * i, () -> title(sp, key));
        }
        Scheduler.later(60 + 110 * n, () -> finish(sp, id));
    }

    private static void title(ServerPlayer sp, String key) {
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(15, 70, 25));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(key)));
    }

    private static void finish(ServerPlayer sp, String id) {
        PLAYING.remove(sp.getUUID());
        StoryData d = StoryData.get(sp.server);
        d.endings.add(id);
        d.chapter = 5;
        if (!id.equals("wake")) d.stayed = true;
        d.setDirty();
        sp.removeEffect(MobEffects.DARKNESS);
        switch (id) {
            case "wake" -> {
                Transit.toOverworld(sp);
                placeDetail(sp.serverLevel());
            }
            case "remember" -> Transit.force(sp, Areas.byId("last_room"));
            default -> Transit.force(sp, Areas.byId("fields"));
        }
        StoryManager.notify(sp, "lr.notify.ending", "");
        StoryManager.finish(sp, d);
    }

    /** The small impossible detail left behind in the normal world: a wallpaper pillar, a ceiling light, a memory. */
    private static void placeDetail(ServerLevel ow) {
        BlockPos s = ow.getSharedSpawnPos();
        int x = s.getX() + 3;
        int z = s.getZ() + 3;
        int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockState wall = ModBlocks.WALLPAPER.get().defaultBlockState();
        ow.setBlock(new BlockPos(x, y, z), wall, 3);
        ow.setBlock(new BlockPos(x, y + 1, z), wall, 3);
        ow.setBlock(new BlockPos(x, y + 2, z), ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState(), 3);
        ow.setBlock(new BlockPos(x + 1, y, z),
                ModBlocks.MEMORY_FRAGMENT.get().defaultBlockState().setValue(MemoryFragmentBlock.MEMORY, 0), 3);
    }
}
