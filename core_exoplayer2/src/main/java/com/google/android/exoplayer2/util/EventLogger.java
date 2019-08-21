package com.google.android.exoplayer2.util;

import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.MediaSourceEventListener.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaSourceEventListener.MediaLoadData;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

public class EventLogger implements AnalyticsListener {
  private static final String DEFAULT_TAG = "EventLogger";
  private static final int MAX_TIMELINE_ITEM_LINES = 3;
  private static final NumberFormat TIME_FORMAT;
  @Nullable
  private final MappingTrackSelector trackSelector;
  private final String tag;
  private final Window window;
  private final Period period;
  private final long startTimeMs;

  public EventLogger(@Nullable MappingTrackSelector trackSelector) {
    this(trackSelector, "EventLogger");
  }

  public EventLogger(@Nullable MappingTrackSelector trackSelector, String tag) {
    this.trackSelector = trackSelector;
    this.tag = tag;
    this.window = new Window();
    this.period = new Period();
    this.startTimeMs = SystemClock.elapsedRealtime();
  }

  public void onLoadingChanged(EventTime eventTime, boolean isLoading) {
    this.logd(eventTime, "loading", Boolean.toString(isLoading));
  }

  public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int state) {
    this.logd(eventTime, "state", playWhenReady + ", " + getStateString(state));
  }

  public void onRepeatModeChanged(EventTime eventTime, int repeatMode) {
    this.logd(eventTime, "repeatMode", getRepeatModeString(repeatMode));
  }

  public void onShuffleModeChanged(EventTime eventTime, boolean shuffleModeEnabled) {
    this.logd(eventTime, "shuffleModeEnabled", Boolean.toString(shuffleModeEnabled));
  }

  public void onPositionDiscontinuity(EventTime eventTime, int reason) {
    this.logd(eventTime, "positionDiscontinuity", getDiscontinuityReasonString(reason));
  }

  public void onSeekStarted(EventTime eventTime) {
    this.logd(eventTime, "seekStarted");
  }

  public void onPlaybackParametersChanged(EventTime eventTime, PlaybackParameters playbackParameters) {
    this.logd(eventTime, "playbackParameters", Util.formatInvariant("speed=%.2f, pitch=%.2f, skipSilence=%s", new Object[]{playbackParameters.speed, playbackParameters.pitch, playbackParameters.skipSilence}));
  }

  public void onTimelineChanged(EventTime eventTime, int reason) {
    int periodCount = eventTime.timeline.getPeriodCount();
    int windowCount = eventTime.timeline.getWindowCount();
    this.logd("timelineChanged [" + this.getEventTimeString(eventTime) + ", periodCount=" + periodCount + ", windowCount=" + windowCount + ", reason=" + getTimelineChangeReasonString(reason));

    int i;
    for(i = 0; i < Math.min(periodCount, 3); ++i) {
      eventTime.timeline.getPeriod(i, this.period);
      this.logd("  period [" + getTimeString(this.period.getDurationMs()) + "]");
    }

    if (periodCount > 3) {
      this.logd("  ...");
    }

    for(i = 0; i < Math.min(windowCount, 3); ++i) {
      eventTime.timeline.getWindow(i, this.window);
      this.logd("  window [" + getTimeString(this.window.getDurationMs()) + ", " + this.window.isSeekable + ", " + this.window.isDynamic + "]");
    }

    if (windowCount > 3) {
      this.logd("  ...");
    }

    this.logd("]");
  }

  public void onPlayerError(EventTime eventTime, ExoPlaybackException e) {
    this.loge(eventTime, "playerFailed", e);
  }

  public void onTracksChanged(EventTime eventTime, TrackGroupArray ignored, TrackSelectionArray trackSelections) {
    MappedTrackInfo mappedTrackInfo = this.trackSelector != null ? this.trackSelector.getCurrentMappedTrackInfo() : null;
    if (mappedTrackInfo == null) {
      this.logd(eventTime, "tracksChanged", "[]");
    } else {
      this.logd("tracksChanged [" + this.getEventTimeString(eventTime) + ", ");
      int rendererCount = mappedTrackInfo.getRendererCount();

      int selectionIndex;
      String formatSupport;
      for(int rendererIndex = 0; rendererIndex < rendererCount; ++rendererIndex) {
        TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
        TrackSelection trackSelection = trackSelections.get(rendererIndex);
        if (rendererTrackGroups.length > 0) {
          this.logd("  Renderer:" + rendererIndex + " [");

          for(selectionIndex = 0; selectionIndex < rendererTrackGroups.length; ++selectionIndex) {
            TrackGroup trackGroup = rendererTrackGroups.get(selectionIndex);
            formatSupport = getAdaptiveSupportString(trackGroup.length, mappedTrackInfo.getAdaptiveSupport(rendererIndex, selectionIndex, false));
            this.logd("    Group:" + selectionIndex + ", adaptive_supported=" + formatSupport + " [");

            for(int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
              String status = getTrackStatusString(trackSelection, trackGroup, trackIndex);
              String formatSupport = getFormatSupportString(mappedTrackInfo.getTrackSupport(rendererIndex, selectionIndex, trackIndex));
              this.logd("      " + status + " Track:" + trackIndex + ", " + Format.toLogString(trackGroup.getFormat(trackIndex)) + ", supported=" + formatSupport);
            }

            this.logd("    ]");
          }

          if (trackSelection != null) {
            for(selectionIndex = 0; selectionIndex < trackSelection.length(); ++selectionIndex) {
              Metadata metadata = trackSelection.getFormat(selectionIndex).metadata;
              if (metadata != null) {
                this.logd("    Metadata [");
                this.printMetadata(metadata, "      ");
                this.logd("    ]");
                break;
              }
            }
          }

          this.logd("  ]");
        }
      }

      TrackGroupArray unassociatedTrackGroups = mappedTrackInfo.getUnmappedTrackGroups();
      if (unassociatedTrackGroups.length > 0) {
        this.logd("  Renderer:None [");

        for(int groupIndex = 0; groupIndex < unassociatedTrackGroups.length; ++groupIndex) {
          this.logd("    Group:" + groupIndex + " [");
          TrackGroup trackGroup = unassociatedTrackGroups.get(groupIndex);

          for(selectionIndex = 0; selectionIndex < trackGroup.length; ++selectionIndex) {
            String status = getTrackStatusString(false);
            formatSupport = getFormatSupportString(0);
            this.logd("      " + status + " Track:" + selectionIndex + ", " + Format.toLogString(trackGroup.getFormat(selectionIndex)) + ", supported=" + formatSupport);
          }

          this.logd("    ]");
        }

        this.logd("  ]");
      }

      this.logd("]");
    }
  }

  public void onSeekProcessed(EventTime eventTime) {
    this.logd(eventTime, "seekProcessed");
  }

  public void onMetadata(EventTime eventTime, Metadata metadata) {
    this.logd("metadata [" + this.getEventTimeString(eventTime) + ", ");
    this.printMetadata(metadata, "  ");
    this.logd("]");
  }

  public void onDecoderEnabled(EventTime eventTime, int trackType, DecoderCounters counters) {
    this.logd(eventTime, "decoderEnabled", getTrackTypeString(trackType));
  }

  public void onAudioSessionId(EventTime eventTime, int audioSessionId) {
    this.logd(eventTime, "audioSessionId", Integer.toString(audioSessionId));
  }

  public void onDecoderInitialized(EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {
    this.logd(eventTime, "decoderInitialized", getTrackTypeString(trackType) + ", " + decoderName);
  }

  public void onDecoderInputFormatChanged(EventTime eventTime, int trackType, Format format) {
    this.logd(eventTime, "decoderInputFormatChanged", getTrackTypeString(trackType) + ", " + Format.toLogString(format));
  }

  public void onDecoderDisabled(EventTime eventTime, int trackType, DecoderCounters counters) {
    this.logd(eventTime, "decoderDisabled", getTrackTypeString(trackType));
  }

  public void onAudioUnderrun(EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    this.loge(eventTime, "audioTrackUnderrun", bufferSize + ", " + bufferSizeMs + ", " + elapsedSinceLastFeedMs + "]", (Throwable)null);
  }

  public void onDroppedVideoFrames(EventTime eventTime, int count, long elapsedMs) {
    this.logd(eventTime, "droppedFrames", Integer.toString(count));
  }

  public void onVideoSizeChanged(EventTime eventTime, int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
    this.logd(eventTime, "videoSizeChanged", width + ", " + height);
  }

  public void onRenderedFirstFrame(EventTime eventTime, @Nullable Surface surface) {
    this.logd(eventTime, "renderedFirstFrame", String.valueOf(surface));
  }

  public void onMediaPeriodCreated(EventTime eventTime) {
    this.logd(eventTime, "mediaPeriodCreated");
  }

  public void onMediaPeriodReleased(EventTime eventTime) {
    this.logd(eventTime, "mediaPeriodReleased");
  }

  public void onLoadStarted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
  }

  public void onLoadError(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
    this.printInternalError(eventTime, "loadError", error);
  }

  public void onLoadCanceled(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
  }

  public void onLoadCompleted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
  }

  public void onReadingStarted(EventTime eventTime) {
    this.logd(eventTime, "mediaPeriodReadingStarted");
  }

  public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
  }

  public void onSurfaceSizeChanged(EventTime eventTime, int width, int height) {
    this.logd(eventTime, "surfaceSizeChanged", width + ", " + height);
  }

  public void onUpstreamDiscarded(EventTime eventTime, MediaLoadData mediaLoadData) {
    this.logd(eventTime, "upstreamDiscarded", Format.toLogString(mediaLoadData.trackFormat));
  }

  public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
    this.logd(eventTime, "downstreamFormatChanged", Format.toLogString(mediaLoadData.trackFormat));
  }

  public void onDrmSessionAcquired(EventTime eventTime) {
    this.logd(eventTime, "drmSessionAcquired");
  }

  public void onDrmSessionManagerError(EventTime eventTime, Exception e) {
    this.printInternalError(eventTime, "drmSessionManagerError", e);
  }

  public void onDrmKeysRestored(EventTime eventTime) {
    this.logd(eventTime, "drmKeysRestored");
  }

  public void onDrmKeysRemoved(EventTime eventTime) {
    this.logd(eventTime, "drmKeysRemoved");
  }

  public void onDrmKeysLoaded(EventTime eventTime) {
    this.logd(eventTime, "drmKeysLoaded");
  }

  public void onDrmSessionReleased(EventTime eventTime) {
    this.logd(eventTime, "drmSessionReleased");
  }

  protected void logd(String msg) {
    Log.d(this.tag, msg);
  }

  protected void loge(String msg, @Nullable Throwable tr) {
    Log.e(this.tag, msg, tr);
  }

  private void logd(EventTime eventTime, String eventName) {
    this.logd(this.getEventString(eventTime, eventName));
  }

  private void logd(EventTime eventTime, String eventName, String eventDescription) {
    this.logd(this.getEventString(eventTime, eventName, eventDescription));
  }

  private void loge(EventTime eventTime, String eventName, @Nullable Throwable throwable) {
    this.loge(this.getEventString(eventTime, eventName), throwable);
  }

  private void loge(EventTime eventTime, String eventName, String eventDescription, @Nullable Throwable throwable) {
    this.loge(this.getEventString(eventTime, eventName, eventDescription), throwable);
  }

  private void printInternalError(EventTime eventTime, String type, Exception e) {
    this.loge(eventTime, "internalError", type, e);
  }

  private void printMetadata(Metadata metadata, String prefix) {
    for(int i = 0; i < metadata.length(); ++i) {
      this.logd(prefix + metadata.get(i));
    }

  }

  private String getEventString(EventTime eventTime, String eventName) {
    return eventName + " [" + this.getEventTimeString(eventTime) + "]";
  }

  private String getEventString(EventTime eventTime, String eventName, String eventDescription) {
    return eventName + " [" + this.getEventTimeString(eventTime) + ", " + eventDescription + "]";
  }

  private String getEventTimeString(EventTime eventTime) {
    String windowPeriodString = "window=" + eventTime.windowIndex;
    if (eventTime.mediaPeriodId != null) {
      windowPeriodString = windowPeriodString + ", period=" + eventTime.timeline.getIndexOfPeriod(eventTime.mediaPeriodId.periodUid);
      if (eventTime.mediaPeriodId.isAd()) {
        windowPeriodString = windowPeriodString + ", adGroup=" + eventTime.mediaPeriodId.adGroupIndex;
        windowPeriodString = windowPeriodString + ", ad=" + eventTime.mediaPeriodId.adIndexInAdGroup;
      }
    }

    return getTimeString(eventTime.realtimeMs - this.startTimeMs) + ", " + getTimeString(eventTime.currentPlaybackPositionMs) + ", " + windowPeriodString;
  }

  private static String getTimeString(long timeMs) {
    return timeMs == -9223372036854775807L ? "?" : TIME_FORMAT.format((double)((float)timeMs / 1000.0F));
  }

  private static String getStateString(int state) {
    switch(state) {
      case 1:
        return "IDLE";
      case 2:
        return "BUFFERING";
      case 3:
        return "READY";
      case 4:
        return "ENDED";
      default:
        return "?";
    }
  }

  private static String getFormatSupportString(int formatSupport) {
    switch(formatSupport) {
      case 0:
        return "NO";
      case 1:
        return "NO_UNSUPPORTED_TYPE";
      case 2:
        return "NO_UNSUPPORTED_DRM";
      case 3:
        return "NO_EXCEEDS_CAPABILITIES";
      case 4:
        return "YES";
      default:
        return "?";
    }
  }

  private static String getAdaptiveSupportString(int trackCount, int adaptiveSupport) {
    if (trackCount < 2) {
      return "N/A";
    } else {
      switch(adaptiveSupport) {
        case 0:
          return "NO";
        case 8:
          return "YES_NOT_SEAMLESS";
        case 16:
          return "YES";
        default:
          return "?";
      }
    }
  }

  private static String getTrackStatusString(@Nullable TrackSelection selection, TrackGroup group, int trackIndex) {
    return getTrackStatusString(selection != null && selection.getTrackGroup() == group && selection.indexOf(trackIndex) != -1);
  }

  private static String getTrackStatusString(boolean enabled) {
    return enabled ? "[X]" : "[ ]";
  }

  private static String getRepeatModeString(int repeatMode) {
    switch(repeatMode) {
      case 0:
        return "OFF";
      case 1:
        return "ONE";
      case 2:
        return "ALL";
      default:
        return "?";
    }
  }

  private static String getDiscontinuityReasonString(int reason) {
    switch(reason) {
      case 0:
        return "PERIOD_TRANSITION";
      case 1:
        return "SEEK";
      case 2:
        return "SEEK_ADJUSTMENT";
      case 3:
        return "AD_INSERTION";
      case 4:
        return "INTERNAL";
      default:
        return "?";
    }
  }

  private static String getTimelineChangeReasonString(int reason) {
    switch(reason) {
      case 0:
        return "PREPARED";
      case 1:
        return "RESET";
      case 2:
        return "DYNAMIC";
      default:
        return "?";
    }
  }

  private static String getTrackTypeString(int trackType) {
    switch(trackType) {
      case 0:
        return "default";
      case 1:
        return "audio";
      case 2:
        return "video";
      case 3:
        return "text";
      case 4:
        return "metadata";
      case 5:
        return "camera motion";
      case 6:
        return "none";
      default:
        return trackType >= 10000 ? "custom (" + trackType + ")" : "?";
    }
  }

  static {
    TIME_FORMAT = NumberFormat.getInstance(Locale.US);
    TIME_FORMAT.setMinimumFractionDigits(2);
    TIME_FORMAT.setMaximumFractionDigits(2);
    TIME_FORMAT.setGroupingUsed(false);
  }

