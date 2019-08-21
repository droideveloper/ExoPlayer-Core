package com.google.android.exoplayer2.util;

import android.os.Looper;
import android.os.Handler.Callback;
import androidx.annotation.Nullable;

public interface Clock {
  Clock DEFAULT = new SystemClock();

  long elapsedRealtime();

  long uptimeMillis();

  void sleep(long var1);

  HandlerWrapper createHandler(Looper var1, @Nullable Callback var2);
}

