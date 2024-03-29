package com.google.android.exoplayer2.upstream;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.HttpDataSource.BaseFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource.RequestProperties;
import com.google.android.exoplayer2.util.Predicate;

public final class DefaultHttpDataSourceFactory extends BaseFactory {
  private final String userAgent;
  @Nullable
  private final TransferListener listener;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private final boolean allowCrossProtocolRedirects;

  public DefaultHttpDataSourceFactory(String userAgent) {
    this(userAgent, (TransferListener)null);
  }

  public DefaultHttpDataSourceFactory(String userAgent, @Nullable TransferListener listener) {
    this(userAgent, listener, 8000, 8000, false);
  }

  public DefaultHttpDataSourceFactory(String userAgent, int connectTimeoutMillis, int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
    this(userAgent, (TransferListener)null, connectTimeoutMillis, readTimeoutMillis, allowCrossProtocolRedirects);
  }

  public DefaultHttpDataSourceFactory(String userAgent, @Nullable TransferListener listener, int connectTimeoutMillis, int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
    this.userAgent = userAgent;
    this.listener = listener;
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
    this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
  }

  protected DefaultHttpDataSource createDataSourceInternal(RequestProperties defaultRequestProperties) {
    DefaultHttpDataSource dataSource = new DefaultHttpDataSource(this.userAgent, (Predicate)null, this.connectTimeoutMillis, this.readTimeoutMillis, this.allowCrossProtocolRedirects, defaultRequestProperties);
    if (this.listener != null) {
      dataSource.addTransferListener(this.listener);
    }

    return dataSource;
  }
}
