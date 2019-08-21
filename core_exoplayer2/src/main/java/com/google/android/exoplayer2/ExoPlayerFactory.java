package com.google.android.exoplayer2;

import android.content.Context;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.analytics.AnalyticsCollector.Factory;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter.Builder;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;

public final class ExoPlayerFactory {
  @Nullable
  private static BandwidthMeter singletonBandwidthMeter;

  private ExoPlayerFactory() {
  }

  /** @deprecated */
  @Deprecated
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector, LoadControl loadControl) {
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context);
    return newSimpleInstance(context, (RenderersFactory)renderersFactory, (TrackSelector)trackSelector, (LoadControl)loadControl);
  }

  /** @deprecated */
  @Deprecated
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context);
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager);
  }

  /** @deprecated */
  @Deprecated
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, int extensionRendererMode) {
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context, extensionRendererMode);
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager);
  }

  /** @deprecated */
  @Deprecated
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, int extensionRendererMode, long allowedVideoJoiningTimeMs) {
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context, extensionRendererMode, allowedVideoJoiningTimeMs);
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager);
  }

  public static SimpleExoPlayer newSimpleInstance(Context context) {
    return newSimpleInstance((Context)context, new DefaultTrackSelector());
  }

  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector) {
    return newSimpleInstance(context, (RenderersFactory)(new DefaultRenderersFactory(context)), (TrackSelector)trackSelector);
  }

  /** @deprecated */
  @Deprecated
  public static SimpleExoPlayer newSimpleInstance(RenderersFactory renderersFactory, TrackSelector trackSelector) {
    return newSimpleInstance((Context)null, (RenderersFactory)renderersFactory, (TrackSelector)trackSelector, (LoadControl)(new DefaultLoadControl()));
  }

  public static SimpleExoPlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector) {
    return newSimpleInstance(context, (RenderersFactory)renderersFactory, (TrackSelector)trackSelector, (LoadControl)(new DefaultLoadControl()));
  }

  public static SimpleExoPlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    return newSimpleInstance(context, renderersFactory, trackSelector, new DefaultLoadControl(), drmSessionManager);
  }

  public static SimpleExoPlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl) {
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, (DrmSessionManager)null, (Looper)Util.getLooper());
  }

  public static SimpleExoPlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager, Util.getLooper());
  }

  public static SimpleExoPlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, BandwidthMeter bandwidthMeter) {
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager, bandwidthMeter, new Factory(), Util.getLooper());
  }

  public static SimpleExoPlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Factory analyticsCollectorFactory) {
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager, analyticsCollectorFactory, Util.getLooper());
  }

  public static SimpleExoPlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Looper looper) {
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager, new Factory(), looper);
  }

  public static SimpleExoPlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Factory analyticsCollectorFactory, Looper looper) {
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager, getDefaultBandwidthMeter(), analyticsCollectorFactory, looper);
  }

  public static SimpleExoPlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, BandwidthMeter bandwidthMeter, Factory analyticsCollectorFactory, Looper looper) {
    return new SimpleExoPlayer(context, renderersFactory, trackSelector, loadControl, drmSessionManager, bandwidthMeter, analyticsCollectorFactory, looper);
  }

  public static ExoPlayer newInstance(Renderer[] renderers, TrackSelector trackSelector) {
    return newInstance(renderers, trackSelector, new DefaultLoadControl());
  }

  public static ExoPlayer newInstance(Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl) {
    return newInstance(renderers, trackSelector, loadControl, Util.getLooper());
  }

  public static ExoPlayer newInstance(Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl, Looper looper) {
    return newInstance(renderers, trackSelector, loadControl, getDefaultBandwidthMeter(), looper);
  }

  public static ExoPlayer newInstance(Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl, BandwidthMeter bandwidthMeter, Looper looper) {
    return new ExoPlayerImpl(renderers, trackSelector, loadControl, bandwidthMeter, Clock.DEFAULT, looper);
  }

  private static synchronized BandwidthMeter getDefaultBandwidthMeter() {
    if (singletonBandwidthMeter == null) {
      singletonBandwidthMeter = (new Builder()).build();
    }

    return singletonBandwidthMeter;
  }
}