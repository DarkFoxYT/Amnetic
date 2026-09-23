package com.meekdev.amnetic.client.shadow;

import com.meekdev.amnetic.client.shadow.internal.ShadowMapPass;

public final class Shadows {

    private static boolean enabled;

    private Shadows() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }
    public static void enable() { enabled = true; }
    public static void disable() { enabled = false; }

    /** how many steps ShadowSettings.budgetMs has taken the sun shadow down, 0 = as set */
    public static int reduction() { return ShadowMapPass.INSTANCE.reduction(); }

    public static float lastBakeMs() { return ShadowMapPass.INSTANCE.lastBakeMs(); }

    public static void dispose() {
        ShadowMapPass.INSTANCE.dispose();
    }
}
