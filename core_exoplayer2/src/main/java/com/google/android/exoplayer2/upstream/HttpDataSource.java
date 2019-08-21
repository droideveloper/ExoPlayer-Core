package com.google.android.exoplayer2.upstream;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Predicate;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface HttpDataSource extends DataSource {
  Predicate<String> REJECT_PAYWALL_TYPES = (contentType) -> {
    contentType = Util.toLowerInvariant(contentType);
    return !TextUtils.isEmpty(contentType) && (!contentType.contains("text") || contentType.contains("text/vtt")) && !contentType.contains("html") && !contentType.contains("xml");
  };

  long open(DataSpec var1) throws HttpDataSource.HttpDataSourceException;

  void close() throws HttpDataSource.HttpDataSourceException;

  int read(byte[] var1, int var2, int var3) throws HttpDataSource.HttpDataSourceException;

  void setRequestProperty(String var1, String var2);

  void clearRequestProperty(String var1);

  void clearAllRequestProperties();

  Map<String, List<String>> getResponseHeaders();

  public static final class InvalidResponseCodeException extends HttpDataSource.HttpDataSourceException {
    public final int responseCode;
    @Nullable
    public final String responseMessage;
    public final Map<String, List<String>> headerFields;

    /** @deprecated */
    @Deprecated
    public InvalidResponseCodeException(int responseCode, Map<String, List<String>> headerFields, DataSpec dataSpec) {
      this(responseCode, (String)null, headerFields, dataSpec);
    }

    public InvalidResponseCodeException(int responseCode, @Nullable String responseMessage, Map<String, List<String>> headerFields, DataSpec dataSpec) {
      super((String)("Response code: " + responseCode), dataSpec, 1);
      this.responseCode = responseCode;
      this.responseMessage = responseMessage;
      this.headerFields = headerFields;
    }
  }

  public static final class InvalidContentTypeException extends HttpDataSource.HttpDataSourceException {
    public final String contentType;

    public InvalidContentTypeException(String contentType, DataSpec dataSpec) {
      super((String)("Invalid content type: " + contentType), dataSpec, 1);
      this.contentType = contentType;
    }
  }

  public static class HttpDataSourceException extends IOException {
    public static final int TYPE_OPEN = 1;
    public static final int TYPE_READ = 2;
    public static final int TYPE_CLOSE = 3;
    public final int type;
    public final DataSpec dataSpec;

    public HttpDataSourceException(DataSpec dataSpec, int type) {
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(String message, DataSpec dataSpec, int type) {
      super(message);
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(IOException cause, DataSpec dataSpec, int type) {
      super(cause);
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(String message, IOException cause, DataSpec dataSpec, int type) {
      super(message, cause);
      this.dataSpec = dataSpec;
      this.type = type;
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
    }
  }

  public abstract static class BaseFactory implements HttpDataSource.Factory {
    private final HttpDataSource.RequestProperties defaultRequestProperties = new HttpDataSource.RequestProperties();

    public BaseFactory() {
    }

    public final HttpDataSource createDataSource() {
      return this.createDataSourceInternal(this.defaultRequestProperties);
    }

    public final HttpDataSource.RequestProperties getDefaultRequestProperties() {
      return this.defaultRequestProperties;
    }

    /** @deprecated */
    @Deprecated
    public final void setDefaultRequestProperty(String name, String value) {
      this.defaultRequestProperties.set(name, value);
    }

    /** @deprecated */
    @Deprecated
    public final void clearDefaultRequestProperty(String name) {
      this.defaultRequestProperties.remove(name);
    }

    /** @deprecated */
    @Deprecated
    public final void clearAllDefaultRequestProperties() {
      this.defaultRequestProperties.clear();
    }

    protected abstract HttpDataSource createDataSourceInternal(HttpDataSource.RequestProperties var1);
  }

  public static final class RequestProperties {
    private final Map<String, String> requestProperties = new HashMap();
    private Map<String, String> requestPropertiesSnapshot;

    public RequestProperties() {
    }

    public synchronized void set(String name, String value) {
      this.requestPropertiesSnapshot = null;
      this.requestProperties.put(name, value);
    }

    public synchronized void set(Map<String, String> properties) {
      this.requestPropertiesSnapshot = null;
      this.requestProperties.putAll(properties);
    }

    public synchronized void clearAndSet(Map<String, String> properties) {
      this.requestPropertiesSnapshot = null;
      this.requestProperties.clear();
      this.requestProperties.putAll(properties);
    }

    public synchronized void remove(String name) {
      this.requestPropertiesSnapshot = null;
      this.requestProperties.remove(name);
    }

    public synchronized void clear() {
      this.requestPropertiesSnapshot = null;
      this.requestProperties.clear();
    }

    public synchronized Map<String, String> getSnapshot() {
      if (this.requestPropertiesSnapshot == null) {
        this.requestPropertiesSnapshot = Collections.unmodifiableMap(new HashMap(this.requestProperties));
      }

      return this.requestPropertiesSnapshot;
    }
  }

  public interface Factory extends com.google.android.exoplayer2.upstream.DataSource.Factory {
    HttpDataSource createDataSource();

    HttpDataSource.RequestProperties getDefaultRequestProperties();

    /** @deprecated */
    @Deprecated
    void setDefaultRequestProperty(String var1, String var2);

    /** @deprecated */
    @Deprecated
    void clearDefaultRequestProperty(String var1);

    /** @deprecated */
    @Deprecated
    void clearAllDefaultRequestProperties();
  }
}
