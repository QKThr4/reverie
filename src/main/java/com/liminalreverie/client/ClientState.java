package com.liminalreverie.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Client-only presentation state: the last journal snapshot, fading notifications and timed visual events. */
public final class ClientState {
    private record Note(Component text, long start) {}

    private static final long NOTE_LIFE_MS = 4500L;
    private static final long NOTE_FADE_MS = 1000L;
    private static final List<Note> NOTES = new ArrayList<>();

    public static CompoundTag snapshot = new CompoundTag();
    public static long silenceUntil = 0L;
    public static long fogShiftUntil = 0L;
    public static long skyBlushUntil = 0L;

    private ClientState() {}

    public static void addNote(Component c) {
        NOTES.add(new Note(c, Util.getMillis()));
        while (NOTES.size() > 4) NOTES.remove(0);
    }

    /** Subtle top-centre notifications that fade away; never a permanent tracker. */
    public static void renderNotes(GuiGraphics g) {
        if (NOTES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        int y = 18;
        for (Iterator<Note> it = NOTES.iterator(); it.hasNext(); ) {
            Note n = it.next();
            long age = now - n.start();
            if (age > NOTE_LIFE_MS) {
                it.remove();
                continue;
            }
            long left = NOTE_LIFE_MS - age;
            double f = left < NOTE_FADE_MS ? left / (double) NOTE_FADE_MS : 1.0;
            int alpha = Math.max(8, (int) (255 * f));
            g.drawCenteredString(mc.font, n.text(), g.guiWidth() / 2, y, (alpha << 24) | 0xE8DCC0);
            y += 12;
        }
    }
}
