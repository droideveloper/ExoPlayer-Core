package com.google.android.exoplayer2.upstream;

import android.os.Handler;
import androidx.annotation.Nullable;

public interface BandwidthMeter {
  long getBitrateEstimate();

  @Nullable
  TransferListener getTransferListener();

  void addEventListener(Handler var1, BandwidthMeter.EventListener var2);

  void removeEventListener(BandwidthMeter.EventListener var1);

  public interface EventListener {
    void onBandwidthSample(int var1, long var2, long var4);
  }
}