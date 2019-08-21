package com.google.android.exoplayer2.upstream.cache;

import com.google.android.exoplayer2.upstream.cache.Cache.Listener;

public interface CacheEvictor extends Listener {
  void onCacheInitialized();

  void onStartFile(Cache var1, String var2, long var3, long var5);
}
