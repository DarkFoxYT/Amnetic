package com.meekdev.amnetic.client.pipeline;

import com.meekdev.amnetic.client.pipeline.internal.GpuTimer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Pipeline {

    private static volatile boolean gpuTimings;

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/Pipeline");

    private static final Map<RenderStage, CopyOnWriteArrayList<PassHandle>> STAGES = new EnumMap<>(RenderStage.class);
    private static final Map<RenderLayer, CopyOnWriteArrayList<LayerPass>> LAYERS = new EnumMap<>(RenderLayer.class);

    static {
        for (RenderStage s : RenderStage.values()) {
            STAGES.put(s, new CopyOnWriteArrayList<>());
        }
        for (RenderLayer l : RenderLayer.values()) {
            LAYERS.put(l, new CopyOnWriteArrayList<>());
        }
    }

    private Pipeline() {}

    // register a pass in a stage at the default order (0)
    public static PassHandle add(RenderStage stage, RenderPass pass) {
        return add(stage, 0, pass);
    }

    // register a pass in a stage; lower order runs earlier, ties keep registration order
    public static PassHandle add(RenderStage stage, int order, RenderPass pass) {
        return add(stage, order, null, pass);
    }

    // same but with an explicit label for the profiler; lambdas otherwise show up under an unreadable
    // synthetic class name
    public static PassHandle add(RenderStage stage, int order, String label, RenderPass pass) {
        PassHandle handle = new PassHandle(stage, pass, order, label);
        CopyOnWriteArrayList<PassHandle> list = STAGES.get(stage);
        // keep the list sorted by order so runStage doesn't re-sort every frame
        int i = 0;
        while (i < list.size() && list.get(i).order <= order) i++;
        list.add(i, handle);
        return handle;
    }

    static void remove(PassHandle handle) {
        handle.removed = true;
        STAGES.get(handle.stage).remove(handle);
    }

    // register a pass that processes an isolated screen layer (hand / GUI). the layer is captured into its
    // own transparent texture, the pass processes it, and the pipeline composites it back over the scene.
    // layers with no registered pass are never captured, so this is zero-cost until used
    public static void addLayer(RenderLayer layer, LayerPass pass) {
        LAYERS.get(layer).add(pass);
    }

    public static void removeLayer(RenderLayer layer, LayerPass pass) {
        LAYERS.get(layer).remove(pass);
    }

    public static boolean hasLayer(RenderLayer layer) {
        return !LAYERS.get(layer).isEmpty();
    }

    public static List<LayerPass> layerPasses(RenderLayer layer) {
        return LAYERS.get(layer);
    }

    // run a screen-space stage (GUI-area hooks) with no level-render context; passes get the current camera
    public static void runStage(RenderStage stage) {
        if (STAGES.get(stage).isEmpty()) return;
        runStage(stage, new FrameContext(CameraSnapshot.current(), null, 0));
    }

    // keep the profiler timing every pass, whether or not anyone watches it
    public static void gpuTimings(boolean enabled) {
        gpuTimings = enabled;
    }

    public static boolean gpuTimings() {
        return gpuTimings;
    }

    // true if a stage has any registered pass, lets callers skip work entirely when nothing is registered
    public static boolean has(RenderStage stage) {
        return !STAGES.get(stage).isEmpty();
    }

    // run every enabled pass in a stage, in order. never throws - a failing pass is logged and skipped
    public static void runStage(RenderStage stage, FrameContext ctx) {
        List<PassHandle> list = STAGES.get(stage);
        if (list.isEmpty()) return;
        // nothing is timed unless the profiler is watched; GL_TIME_ELAPSED queries serialize the driver
        // around every pass, so they only run then
        boolean profiling = PassProfiler.INSTANCE.active();
        if (profiling && stage == RenderStage.SETUP) PassProfiler.INSTANCE.frame();
        for (PassHandle h : list) {
            if (h.removed || !h.enabled) continue;
            RenderPass pass = h.pass;
            if (!safeEnabled(pass, stage)) continue;
            long start = 0;
            boolean timed = false;
            if (profiling) {
                if (h.gpuTimer == null) h.gpuTimer = new GpuTimer();
                float gpuMs = h.gpuTimer.poll();
                if (gpuMs >= 0f) h.profile().gpu(gpuMs);
                timed = h.gpuTimer.begin();
                start = System.nanoTime();
            }
            try {
                pass.render(ctx);
            } catch (Exception e) {
                LOGGER.error("pass in stage {} threw; skipping it this frame", stage, e);
            } finally {
                if (profiling) {
                    if (timed) h.gpuTimer.end();
                    h.profile().cpu(System.nanoTime() - start);
                }
            }
        }
        // clean baseline after the stage so the next stage (and vanilla) start from known state
        GlState.endFullscreen();
    }

    private static boolean safeEnabled(RenderPass pass, RenderStage stage) {
        try {
            return pass.enabled();
        } catch (Exception e) {
            LOGGER.error("pass.enabled() in stage {} threw; skipping it", stage, e);
            return false;
        }
    }
}
