package com.google.android.exoplayer2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.android.exoplayer2.video.spherical.CameraMotionRenderer;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.util.ArrayList;

public class DefaultRenderersFactory implements RenderersFactory {
  public static final long DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS = 5000L;
  public static final int EXTENSION_RENDERER_MODE_OFF = 0;
  public static final int EXTENSION_RENDERER_MODE_ON = 1;
  public static final int EXTENSION_RENDERER_MODE_PREFER = 2;
  private static final String TAG = "DefaultRenderersFactory";
  protected static final int MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 50;
  private final Context context;
  @Nullable
  private final DrmSessionManager<FrameworkMediaCrypto> drmSessionManager;
  private final int extensionRendererMode;
  private final long allowedVideoJoiningTimeMs;

  public DefaultRenderersFactory(Context context) {
    this(context, 0);
  }

  /** @deprecated */
  @Deprecated
  public DefaultRenderersFactory(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    this(context, drmSessionManager, 0);
  }

  public DefaultRenderersFactory(Context context, int extensionRendererMode) {
    this(context, extensionRendererMode, 5000L);
  }

  /** @deprecated */
  @Deprecated
  public DefaultRenderersFactory(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, int extensionRendererMode) {
    this(context, drmSessionManager, extensionRendererMode, 5000L);
  }

  public DefaultRenderersFactory(Context context, int extensionRendererMode, long allowedVideoJoiningTimeMs) {
    this.context = context;
    this.extensionRendererMode = extensionRendererMode;
    this.allowedVideoJoiningTimeMs = allowedVideoJoiningTimeMs;
    this.drmSessionManager = null;
  }

  /** @deprecated */
  @Deprecated
  public DefaultRenderersFactory(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, int extensionRendererMode, long allowedVideoJoiningTimeMs) {
    this.context = context;
    this.extensionRendererMode = extensionRendererMode;
    this.allowedVideoJoiningTimeMs = allowedVideoJoiningTimeMs;
    this.drmSessionManager = drmSessionManager;
  }

  public Renderer[] createRenderers(Handler eventHandler, VideoRendererEventListener videoRendererEventListener, AudioRendererEventListener audioRendererEventListener, TextOutput textRendererOutput, MetadataOutput metadataRendererOutput, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    if (drmSessionManager == null) {
      drmSessionManager = this.drmSessionManager;
    }

    ArrayList<Renderer> renderersList = new ArrayList();
    this.buildVideoRenderers(this.context, drmSessionManager, this.allowedVideoJoiningTimeMs, eventHandler, videoRendererEventListener, this.extensionRendererMode, renderersList);
    this.buildAudioRenderers(this.context, drmSessionManager, this.buildAudioProcessors(), eventHandler, audioRendererEventListener, this.extensionRendererMode, renderersList);
    this.buildTextRenderers(this.context, textRendererOutput, eventHandler.getLooper(), this.extensionRendererMode, renderersList);
    this.buildMetadataRenderers(this.context, metadataRendererOutput, eventHandler.getLooper(), this.extensionRendererMode, renderersList);
    this.buildCameraMotionRenderers(this.context, this.extensionRendererMode, renderersList);
    this.buildMiscellaneousRenderers(this.context, eventHandler, this.extensionRendererMode, renderersList);
    return (Renderer[])renderersList.toArray(new Renderer[renderersList.size()]);
  }

  protected void buildVideoRenderers(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, long allowedVideoJoiningTimeMs, Handler eventHandler, VideoRendererEventListener eventListener, int extensionRendererMode, ArrayList<Renderer> out) {
    out.add(new MediaCodecVideoRenderer(context, MediaCodecSelector.DEFAULT, allowedVideoJoiningTimeMs, drmSessionManager, false, eventHandler, eventListener, 50));
    if (extensionRendererMode != 0) {
      int extensionRendererIndex = out.size();
      if (extensionRendererMode == 2) {
        --extensionRendererIndex;
      }

      try {
        Class<?> clazz = Class.forName("com.google.android.exoplayer2.ext.vp9.LibvpxVideoRenderer");
        Constructor<?> constructor = clazz.getConstructor(Boolean.TYPE, Long.TYPE, Handler.class, VideoRendererEventListener.class, Integer.TYPE);
        Renderer renderer = (Renderer)constructor.newInstance(true, allowedVideoJoiningTimeMs, eventHandler, eventListener, 50);
        out.add(extensionRendererIndex++, renderer);
        Log.i("DefaultRenderersFactory", "Loaded LibvpxVideoRenderer.");
      } catch (ClassNotFoundException var13) {
      } catch (Exception var14) {
        throw new RuntimeException("Error instantiating VP9 extension", var14);
      }

    }
  }

  protected void buildAudioRenderers(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, AudioProcessor[] audioProcessors, Handler eventHandler, AudioRendererEventListener eventListener, int extensionRendererMode, ArrayList<Renderer> out) {
    out.add(new MediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT, drmSessionManager, false, eventHandler, eventListener, AudioCapabilities.getCapabilities(context), audioProcessors));
    if (extensionRendererMode != 0) {
      int extensionRendererIndex = out.size();
      if (extensionRendererMode == 2) {
        --extensionRendererIndex;
      }

      Class clazz;
      Constructor constructor;
      Renderer renderer;
      try {
        clazz = Class.forName("com.google.android.exoplayer2.ext.opus.LibopusAudioRenderer");
        constructor = clazz.getConstructor(Handler.class, AudioRendererEventListener.class, AudioProcessor[].class);
        renderer = (Renderer)constructor.newInstance(eventHandler, eventListener, audioProcessors);
        out.add(extensionRendererIndex++, renderer);
        Log.i("DefaultRenderersFactory", "Loaded LibopusAudioRenderer.");
      } catch (ClassNotFoundException var16) {
      } catch (Exception var17) {
        throw new RuntimeException("Error instantiating Opus extension", var17);
      }

      try {
        clazz = Class.forName("com.google.android.exoplayer2.ext.flac.LibflacAudioRenderer");
        constructor = clazz.getConstructor(Handler.class, AudioRendererEventListener.class, AudioProcessor[].class);
        renderer = (Renderer)constructor.newInstance(eventHandler, eventListener, audioProcessors);
        out.add(extensionRendererIndex++, renderer);
        Log.i("DefaultRenderersFactory", "Loaded LibflacAudioRenderer.");
      } catch (ClassNotFoundException var14) {
      } catch (Exception var15) {
        throw new RuntimeException("Error instantiating FLAC extension", var15);
      }

      try {
        clazz = Class.forName("com.google.android.exoplayer2.ext.ffmpeg.FfmpegAudioRenderer");
        constructor = clazz.getConstructor(Handler.class, AudioRendererEventListener.class, AudioProcessor[].class);
        renderer = (Renderer)constructor.newInstance(eventHandler, eventListener, audioProcessors);
        out.add(extensionRendererIndex++, renderer);
        Log.i("DefaultRenderersFactory", "Loaded FfmpegAudioRenderer.");
      } catch (ClassNotFoundException var12) {
      } catch (Exception var13) {
        throw new RuntimeException("Error instantiating FFmpeg extension", var13);
      }

    }
  }

  protected void buildTextRenderers(Context context, TextOutput output, Looper outputLooper, int extensionRendererMode, ArrayList<Renderer> out) {
    out.add(new TextRenderer(output, outputLooper));
  }

  protected void buildMetadataRenderers(Context context, MetadataOutput output, Looper outputLooper, int extensionRendererMode, ArrayList<Renderer> out) {
    out.add(new MetadataRenderer(output, outputLooper));
  }

  protected void buildCameraMotionRenderers(Context context, int extensionRendererMode, ArrayList<Renderer> out) {
    out.add(new CameraMotionRenderer());
  }

  protected void buildMiscellaneousRenderers(Context context, Handler eventHandler, int extensionRendererMode, ArrayList<Renderer> out) {
  }

  protected AudioProcessor[] buildAudioProcessors() {
    return new AudioProcessor[0];
  }

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  public @interface ExtensionRendererMode {
  }
}

