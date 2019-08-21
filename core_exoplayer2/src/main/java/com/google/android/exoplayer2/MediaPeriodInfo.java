package com.google.android.exoplayer2;

import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;

final class MediaPeriodInfo {
  public final MediaPeriodId id;
  public final long startPositionUs;
  public final long contentPositionUs;
  public final long durationUs;
  public final boolean isLastInTimelinePeriod;
  public final boolean isFinal;

  MediaPeriodInfo(MediaPeriodId id, long startPositionUs, long contentPositionUs, long durationUs, boolean isLastInTimelinePeriod, boolean isFinal) {
    this.id = id;
    this.startPositionUs = startPositionUs;
    this.contentPositionUs = contentPositionUs;
    this.durationUs = durationUs;
    this.isLastInTimelinePeriod = isLastInTimelinePeriod;
    this.isFinal = isFinal;
  }

  public MediaPeriodInfo copyWithStartPositionUs(long startPositionUs) {
    return new MediaPeriodInfo(this.id, startPositionUs, this.contentPositionUs, this.durationUs, this.isLastInTimelinePeriod, this.isFinal);
  }
}