package com.google.android.exoplayer2.upstream.cache;

import com.google.android.exoplayer2.upstream.FileDataSourceFactory;
import com.google.android.exoplayer2.upstream.DataSource.Factory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource.EventListener;

public final class CacheDataSourceFactory implements Factory {
  private final Cache cache;
  private final Factory upstreamFactory;
  private final Factory cacheReadDataSourceFactory;
  private final com.google.android.exoplayer2.upstream.DataSink.Factory cacheWriteDataSinkFactory;
  private final int flags;
  private final EventListener eventListener;

  public CacheDataSourceFactory(Cache cache, Factory upstreamFactory) {
    this(cache, upstreamFactory, 0);
  }

  public CacheDataSourceFactory(Cache cache, Factory upstreamFactory, int flags) {
    this(cache, upstreamFactory, flags, 2097152L);
  }

  public CacheDataSourceFactory(Cache cache, Factory upstreamFactory, int flags, long maxCacheFileSize) {
    this(cache, upstreamFactory, new FileDataSourceFactory(), new CacheDataSinkFactory(cache, maxCacheFileSize), flags, (EventListener)null);
  }

  public CacheDataSourceFactory(Cache cache, Factory upstreamFactory, Factory cacheReadDataSourceFactory, com.google.android.exoplayer2.upstream.DataSink.Factory cacheWriteDataSinkFactory, int flags, EventListener eventListener) {
    this.cache = cache;
    this.upstreamFactory = upstreamFactory;
    this.cacheReadDataSourceFactory = cacheReadDataSourceFactory;
    this.cacheWriteDataSinkFactory = cacheWriteDataSinkFactory;
    this.flags = flags;
    this.eventListener = eventListener;
  }

  public CacheDataSource createDataSource() {
    return new CacheDataSource(this.cache, this.upstreamFactory.createDataSource(), this.cacheReadDataSourceFactory.createDataSource(), this.cacheWriteDataSinkFactory != null ? this.cacheWriteDataSinkFactory.createDataSink() : null, this.flags, this.eventListener);
  }
}
