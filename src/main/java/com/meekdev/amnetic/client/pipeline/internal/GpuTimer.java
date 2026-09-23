package com.meekdev.amnetic.client.pipeline.internal;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

// a ring of GL_TIME_ELAPSED queries. CPU nanoTime only measures submit time, not what the GPU actually
// spends, so a GPU-bound pass can look free on the CPU timeline. results are read a few frames later and
// only once the driver says they are ready, so reading never stalls the frame
public final class GpuTimer {

    private static final int RING = 4;

    private final int[] queries = new int[RING];
    private final boolean[] pending = new boolean[RING];
    private int write;
    private int read;

    public GpuTimer() {
        GL15.glGenQueries(queries);
    }

    // false when every query is still in flight; the pass then runs untimed this frame
    public boolean begin() {
        if (pending[write]) return false;
        GL33.glBeginQuery(GL33.GL_TIME_ELAPSED, queries[write]);
        return true;
    }

    public void end() {
        GL33.glEndQuery(GL33.GL_TIME_ELAPSED);
        pending[write] = true;
        write = (write + 1) % RING;
    }

    // the oldest finished result in ms, -1 when none has landed since the last call
    public float poll() {
        float ms = -1f;
        while (pending[read] && GL15.glGetQueryObjecti(queries[read], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            ms = GL33.glGetQueryObjectui64(queries[read], GL15.GL_QUERY_RESULT) / 1_000_000f;
            pending[read] = false;
            read = (read + 1) % RING;
        }
        return ms;
    }

    public void dispose() {
        GL15.glDeleteQueries(queries);
    }
}
