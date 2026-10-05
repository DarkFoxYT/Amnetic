package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.grade.ColorGrade;
import com.meekdev.amnetic.client.light.Lights;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.Amnetic;
import de.javagl.jgltf.model.io.GltfModelReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.assimp.Assimp;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class RuntimeSmokeMixin {
    @Unique private int amnetic$smokeTicks;
    @Unique private int amnetic$worldTicks;
    @Unique private int amnetic$postFrames;
    @Unique private int amnetic$lightingFrames;
    @Unique private boolean amnetic$installed;
    @Unique private boolean amnetic$librariesChecked;
    @Unique private boolean amnetic$finished;
    @Unique private java.util.concurrent.CompletableFuture<Void> amnetic$reload;

    @Inject(method = "tick", at = @At("TAIL"))
    private void amnetic$runtimeSmoke(CallbackInfo ci) {
        if (amnetic$finished) return;
        Minecraft mc = (Minecraft) (Object) this;
        amnetic$smokeTicks++;
        boolean world = Boolean.getBoolean("amnetic.smoke.world");
        if (!amnetic$librariesChecked && (mc.screen instanceof TitleScreen || mc.level != null)) {
            amnetic$librariesChecked = true;
            try (var input = Amnetic.class.getClassLoader().getResourceAsStream("assets/amnetic/models/pillow.glb")) {
                if (input == null || new GltfModelReader().readWithoutReferences(input).getMeshModels().isEmpty()) {
                    throw new AssertionError("Bundled glTF fixture did not load");
                }
                if (Assimp.aiGetVersionMajor() <= 0) throw new AssertionError("Assimp native library did not load");
                LoggerFactory.getLogger("Amnetic/Smoke").info("SMOKE: glTF model, Jackson and Assimp native libraries loaded");
            } catch (java.io.IOException error) {
                throw new AssertionError("Bundled glTF fixture did not load", error);
            }
        }
        if (!world && mc.screen instanceof TitleScreen && amnetic$smokeTicks > 60) {
            LoggerFactory.getLogger("Amnetic/Smoke").info("SMOKE PASS: title screen loaded");
            amnetic$finished = true;
            mc.stop();
            return;
        }
        if (world && mc.level != null && mc.player != null) {
            if (!amnetic$installed) {
                amnetic$installed = true;
                int before = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
                int outer = GL30.glGenVertexArrays();
                int inner = GL30.glGenVertexArrays();
                try {
                    Class<?> state;
                    try { state = Class.forName("com.mojang.blaze3d.opengl.GlStateManager"); }
                    catch (ClassNotFoundException olderVersion) { state = Class.forName("com.mojang.blaze3d.platform.GlStateManager"); }
                    var bind = state.getMethod("_glBindVertexArray", int.class);
                    bind.invoke(null, outer);
                    GlState.beginFullscreen();
                    bind.invoke(null, inner);
                    GlState.beginFullscreen();
                    bind.invoke(null, before);
                    GlState.endFullscreen();
                    if (GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) != inner) throw new AssertionError("Inner vertex array was not restored");
                    GlState.endFullscreen();
                    if (GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) != outer) throw new AssertionError("Outer vertex array was not restored");
                    bind.invoke(null, before);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("Could not access Minecraft's vertex array state", error);
                } finally {
                    GL30.glDeleteVertexArrays(inner);
                    GL30.glDeleteVertexArrays(outer);
                }
                Bloom.enable();
                ColorGrade.enable();
                ColorGrade.settings().saturation(0.8f);
                Lights.point(mc.player.position().add(0, 2, 0), 1, 0.4f, 0.2f, 12, 2);
                Pipeline.add(RenderStage.POST, Integer.MAX_VALUE, "Runtime smoke", ctx -> amnetic$postFrames++);
                Pipeline.add(RenderStage.LIGHTING, Integer.MAX_VALUE, "Runtime smoke lighting", ctx -> amnetic$lightingFrames++);
                LoggerFactory.getLogger("Amnetic/Smoke").info("SMOKE: world loaded; lighting, bloom and grading enabled");
            }
            amnetic$worldTicks++;
            if (amnetic$worldTicks == 100) {
                amnetic$reload = mc.reloadResourcePacks();
            }
            if (amnetic$worldTicks >= 240 && amnetic$reload != null && amnetic$reload.isDone()) {
                amnetic$reload.join();
                if (amnetic$postFrames < 60) throw new AssertionError("World post-processing hooks did not run");
                if (amnetic$lightingFrames < 60) throw new AssertionError("World lighting hooks did not run");
                if (!Bloom.settings().isEnabled()) throw new AssertionError("Bloom disabled itself after a render failure");
                LoggerFactory.getLogger("Amnetic/Smoke").info("SMOKE PASS: {} world ticks, {} lighting frames, {} post frames, resource reload and nested vertex array restoration", amnetic$worldTicks, amnetic$lightingFrames, amnetic$postFrames);
                amnetic$finished = true;
                mc.stop();
            }
        }
        if (amnetic$smokeTicks > 2400) throw new AssertionError("Runtime smoke test timed out");
    }
}
