package Csekiro.arena.mixin.client;

import Csekiro.arena.hunter.HunterScytheTrackedPlayer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Unique
    private static final Identifier ARENA_CRITICAL_HEART = Identifier.of("arena", "hud/heart/critical");
    @Unique
    private static final Identifier ARENA_CRITICAL_HEART_BLINKING = Identifier.of("arena", "hud/heart/critical_blinking");
    @Unique
    private static final Identifier ARENA_CRITICAL_HEART_HARDCORE = Identifier.of("arena", "hud/heart/critical_hardcore");
    @Unique
    private static final Identifier ARENA_CRITICAL_HEART_HARDCORE_BLINKING = Identifier.of("arena", "hud/heart/critical_hardcore_blinking");
    @Unique
    private static final Class<?> ARENA_HEART_TYPE_CLASS = resolveHeartTypeClass();
    @Unique
    private static final Object ARENA_CONTAINER_HEART = resolveHeartType("CONTAINER");
    @Unique
    private static final Object ARENA_WITHERED_HEART = resolveHeartType("WITHERED");
    @Unique
    private static final Method ARENA_DRAW_HEART = resolveDrawHeartMethod();

    @Inject(method = "renderHealthBar", at = @At("TAIL"))
    private void arena$renderBlackHearts(
            DrawContext context,
            PlayerEntity player,
            int x,
            int y,
            int rowHeight,
            int regenIndex,
            float maxHealth,
            int currentHealth,
            int lastHealth,
            int absorption,
            boolean blinking,
            CallbackInfo ci
    ) {
        if (!(player instanceof HunterScytheTrackedPlayer trackedPlayer)) {
            return;
        }

        float blackHeartAmount = trackedPlayer.arena$getBlackHeartDisplay();
        if (blackHeartAmount <= 0.0F) {
            return;
        }

        boolean hardcore = player.getEntityWorld().getLevelProperties().isHardcore();
        int baseHearts = MathHelper.ceil(maxHealth / 2.0F);
        int maxDisplayUnits = MathHelper.ceil(maxHealth);
        int displayedCurrentHealth = trackedPlayer.arena$isBlackHeartLastStand() ? 0 : MathHelper.ceil(player.getHealth());
        int blackUnits = Math.min(MathHelper.ceil(blackHeartAmount), Math.max(0, maxDisplayUnits - displayedCurrentHealth));
        int fullRedHearts = displayedCurrentHealth / 2;
        boolean hasRedHalfHeart = (displayedCurrentHealth & 1) == 1;
        boolean hasCriticalMixedHeart = hasRedHalfHeart && blackUnits > 0;
        int mixedHeartIndex = fullRedHearts;
        int occupiedRedSlots = fullRedHearts + (hasRedHalfHeart ? 1 : 0);
        int remainingBlackUnits = hasCriticalMixedHeart ? blackUnits - 1 : blackUnits;
        int blackStartHeartIndex = occupiedRedSlots;
        int totalDisplayHearts = Math.max(baseHearts, occupiedRedSlots + MathHelper.ceil(remainingBlackUnits / 2.0F));

        for (int heartIndex = totalDisplayHearts - 1; heartIndex >= 0; heartIndex--) {
            int row = heartIndex / 10;
            int column = heartIndex % 10;
            int heartX = x + column * 8;
            int heartY = y - row * rowHeight;

            if (displayedCurrentHealth + absorption <= 4) {
                heartY += player.getRandom().nextInt(2);
            }

            if (heartIndex < baseHearts && heartIndex == regenIndex) {
                heartY -= 2;
            }

            if (heartIndex >= baseHearts) {
                drawHeart(context, ARENA_CONTAINER_HEART, heartX, heartY, hardcore, false, false);
            }

            if (hasCriticalMixedHeart && heartIndex == mixedHeartIndex) {
                context.drawGuiTexture(
                        RenderPipelines.GUI_TEXTURED,
                        getCriticalHeartTexture(hardcore, blinking),
                        heartX,
                        heartY,
                        9,
                        9
                );
                continue;
            }

            int slotOffset = heartIndex - blackStartHeartIndex;
            if (remainingBlackUnits <= 0 || slotOffset < 0) {
                continue;
            }

            int slotBlackUnits = MathHelper.clamp(remainingBlackUnits - slotOffset * 2, 0, 2);
            if (slotBlackUnits <= 0) {
                continue;
            }

            drawHeart(context, ARENA_WITHERED_HEART, heartX, heartY, hardcore, false, slotBlackUnits == 1);
        }
    }

    @Unique
    private static Identifier getCriticalHeartTexture(boolean hardcore, boolean blinking) {
        if (hardcore) {
            return blinking ? ARENA_CRITICAL_HEART_HARDCORE_BLINKING : ARENA_CRITICAL_HEART_HARDCORE;
        }

        return blinking ? ARENA_CRITICAL_HEART_BLINKING : ARENA_CRITICAL_HEART;
    }

    @Unique
    private static Class<?> resolveHeartTypeClass() {
        try {
            return Class.forName("net.minecraft.client.gui.hud.InGameHud$HeartType");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to resolve heart type", exception);
        }
    }

    @Unique
    private static Object resolveHeartType(String name) {
        Object[] constants = ARENA_HEART_TYPE_CLASS.getEnumConstants();

        for (Object constant : constants) {
            Enum<?> enumValue = (Enum<?>) constant;
            if (enumValue.name().equals(name)) {
                return constant;
            }
        }

        throw new IllegalStateException("Missing heart type " + name);
    }

    @Unique
    private static Method resolveDrawHeartMethod() {
        try {
            Method method = InGameHud.class.getDeclaredMethod(
                    "drawHeart",
                    DrawContext.class,
                    ARENA_HEART_TYPE_CLASS,
                    int.class,
                    int.class,
                    boolean.class,
                    boolean.class,
                    boolean.class
            );
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Failed to resolve drawHeart", exception);
        }
    }

    @Unique
    private void drawHeart(DrawContext context, Object heartType, int x, int y, boolean hardcore, boolean blinking, boolean half) {
        try {
            ARENA_DRAW_HEART.invoke(this, context, heartType, x, y, hardcore, blinking, half);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new RuntimeException("Failed to draw hunter scythe black heart", exception);
        }
    }
}