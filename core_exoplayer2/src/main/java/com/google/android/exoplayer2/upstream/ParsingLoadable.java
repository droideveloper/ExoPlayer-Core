package com.google.android.exoplayer2.upstream;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class ParsingLoadable<T> implements Loadable {
  public final DataSpec dataSpec;
  public final int type;
  public final StatsDataSource dataSource;
  public final ParsingLoadable.Parser<? extends T> parser;
  @Nullable
  protected volatile T result;

  public static <T> T load(DataSource dataSource, ParsingLoadable.Parser<? extends T> parser, Uri uri, int type) throws IOException {
    ParsingLoadable<T> loadable = new ParsingLoadable(dataSource, uri, type, parser);
    loadable.load();
    return Assertions.checkNotNull(loadable.getResult());
  }

  public ParsingLoadable(DataSource dataSource, Uri uri, int type, ParsingLoadable.Parser<? extends T> parser) {
    this(dataSource, new DataSpec(uri, 3), type, parser);
  }

  public ParsingLoadable(DataSource dataSource, DataSpec dataSpec, int type, ParsingLoadable.Parser<? extends T> parser) {
    this.dataSource = new StatsDataSource(dataSource);
    this.dataSpec = dataSpec;
    this.type = type;
    this.parser = parser;
  }

  @Nullable
  public final T getResult() {
    return this.result;
  }

  public long bytesLoaded() {
    return this.dataSource.getBytesRead();
  }

  public Uri getUri() {
    return this.dataSource.getLastOpenedUri();
  }

  public Map<String, List<String>> getResponseHeaders() {
    return this.dataSource.getLastResponseHeaders();
  }

  public final void cancelLoad() {
  }

  public final void load() throws IOException {
    this.dataSource.resetBytesRead();
    DataSourceInputStream inputStream = new DataSourceInputStream(this.dataSource, this.dataSpec);

    try {
      inputStream.open();
      Uri dataSourceUri = (Uri)Assertions.checkNotNull(this.dataSource.getUri());
      this.result = this.parser.parse(dataSourceUri, inputStream);
    } finally {
      Util.closeQuietly(inputStream);
    }

  }

  public interface Parser<T> {
    T parse(Uri var1, InputStream var2) throws IOException;
  }
}

