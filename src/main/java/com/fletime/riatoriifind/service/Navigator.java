package com.fletime.riatoriifind.service;

import net.minecraft.core.BlockPos;

public final class Navigator {

    private static BlockPos target;

    private Navigator() {
        throw new UnsupportedOperationException("Static accessor");
    }

    public static void setTarget(int x, int y, int z) {
        target = new BlockPos(x, y, z);
    }

    public static BlockPos getTarget() {
        return target;
    }

    public static void clear() {
        target = null;
    }

    public static boolean hasTarget() {
        return target != null;
    }
}
