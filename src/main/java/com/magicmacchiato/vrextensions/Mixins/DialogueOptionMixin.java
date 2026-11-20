package com.magicmacchiato.vrextensions.Mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** Makes long choice text fit inside the dialogue option button: wraps to two lines and shrinks slightly. */
@Mixin(targets = "com.javic.slimpatch.client.gui.VillagerDialogueScreen$DialogueOption", remap = false)
public abstract class DialogueOptionMixin {

    @Unique private static final int PADDING = 4;
    @Unique private static final float WRAPPED_SCALE = 0.8F;
    @Unique private static final int MAX_LINES = 2;

    @Redirect(method = "renderWidget",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void vrextensions$drawFittedText(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
        AbstractWidget self = (AbstractWidget) (Object) this;
        int maxWidth = self.getWidth() - PADDING * 2;

        if (font.width(text) <= maxWidth) {
            graphics.drawCenteredString(font, text, centerX, y, color);
            return;
        }

        List<FormattedCharSequence> lines = font.split(text, (int) (maxWidth / WRAPPED_SCALE));
        int count = Math.min(lines.size(), MAX_LINES);
        int lineHeight = font.lineHeight;
        float blockHeight = count * lineHeight * WRAPPED_SCALE;
        float top = self.getY() + (self.getHeight() - blockHeight) / 2.0F;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, top, 0);
        pose.scale(WRAPPED_SCALE, WRAPPED_SCALE, 1.0F);
        for (int i = 0; i < count; i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.drawString(font, line, -font.width(line) / 2, i * lineHeight, color, false);
        }
        pose.popPose();
    }
}
