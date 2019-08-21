package com.google.android.exoplayer2;

import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

public interface RenderersFactory {
  Renderer[] createRenderers(Handler var1, VideoRendererEventListener var2, AudioRendererEventListener var3, TextOutput var4, MetadataOutput var5, @Nullable DrmSessionManager<FrameworkMediaCrypto> var6);
}