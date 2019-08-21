package com.google.android.exoplayer2.upstream;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import androidx.annotation.Nullable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class AssetDataSource extends BaseDataSource {
  private final AssetManager assetManager;
  @Nullable
  private Uri uri;
  @Nullable
  private InputStream inputStream;
  private long bytesRemaining;
  private boolean opened;

  public AssetDataSource(Context context) {
    super(false);
    this.assetManager = context.getAssets();
  }

  /** @deprecated */
  @Deprecated
  public AssetDataSource(Context context, @Nullable TransferListener listener) {
    this(context);
    if (listener != null) {
      this.addTransferListener(listener);
    }

  }

  public long open(DataSpec dataSpec) throws AssetDataSource.AssetDataSourceException {
    try {
      this.uri = dataSpec.uri;
      String path = this.uri.getPath();
      if (path.startsWith("/android_asset/")) {
        path = path.substring(15);
      } else if (path.startsWith("/")) {
        path = path.substring(1);
      }

      this.transferInitializing(dataSpec);
      this.inputStream = this.assetManager.open(path, 1);
      long skipped = this.inputStream.skip(dataSpec.position);
      if (skipped < dataSpec.position) {
        throw new EOFException();
      }

      if (dataSpec.length != -1L) {
        this.bytesRemaining = dataSpec.length;
      } else {
        this.bytesRemaining = (long)this.inputStream.available();
        if (this.bytesRemaining == 2147483647L) {
          this.bytesRemaining = -1L;
        }
      }
    } catch (IOException var5) {
      throw new AssetDataSource.AssetDataSourceException(var5);
    }

    this.opened = true;
    this.transferStarted(dataSpec);
    return this.bytesRemaining;
  }

  public int read(byte[] buffer, int offset, int readLength) throws AssetDataSource.AssetDataSourceException {
    if (readLength == 0) {
      return 0;
    } else if (this.bytesRemaining == 0L) {
      return -1;
    } else {
      int bytesRead;
      try {
        int bytesToRead = this.bytesRemaining == -1L ? readLength : (int)Math.min(this.bytesRemaining, (long)readLength);
        bytesRead = this.inputStream.read(buffer, offset, bytesToRead);
      } catch (IOException var6) {
        throw new AssetDataSource.AssetDataSourceException(var6);
      }

      if (bytesRead == -1) {
        if (this.bytesRemaining != -1L) {
          throw new AssetDataSource.AssetDataSourceException(new EOFException());
        } else {
          return -1;
        }
      } else {
        if (this.bytesRemaining != -1L) {
          this.bytesRemaining -= (long)bytesRead;
        }

        this.bytesTransferred(bytesRead);
        return bytesRead;
      }
    }
  }

  @Nullable
  public Uri getUri() {
    return this.uri;
  }

  public void close() throws AssetDataSource.AssetDataSourceException {
    this.uri = null;

    try {
      if (this.inputStream != null) {
        this.inputStream.close();
      }
    } catch (IOException var5) {
      throw new AssetDataSource.AssetDataSourceException(var5);
    } finally {
      this.inputStream = null;
      if (this.opened) {
        this.opened = false;
        this.transferEnded();
      }

    }

  }

  public static final class AssetDataSourceException extends IOException {
    public AssetDataSourceException(IOException cause) {
      super(cause);
    }
  }
}

