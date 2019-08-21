package com.google.android.exoplayer2.upstream.cache;

import android.net.Uri;
import androidx.annotation.Nullable;

final class ContentMetadataInternal {
  private static final String PREFIX = "exo_";
  private static final String METADATA_NAME_REDIRECTED_URI = "exo_redir";
  private static final String METADATA_NAME_CONTENT_LENGTH = "exo_len";

  public static long getContentLength(ContentMetadata contentMetadata) {
    return contentMetadata.get("exo_len", -1L);
  }

  public static void setContentLength(ContentMetadataMutations mutations, long length) {
    mutations.set("exo_len", length);
  }

  public static void removeContentLength(ContentMetadataMutations mutations) {
    mutations.remove("exo_len");
  }

  @Nullable
  public static Uri getRedirectedUri(ContentMetadata contentMetadata) {
    String redirectedUri = contentMetadata.get("exo_redir", (String)null);
    return redirectedUri == null ? null : Uri.parse(redirectedUri);
  }

  public static void setRedirectedUri(ContentMetadataMutations mutations, Uri uri) {
    mutations.set("exo_redir", uri.toString());
  }

  public static void removeRedirectedUri(ContentMetadataMutations mutations) {
    mutations.remove("exo_redir");
  }

  private ContentMetadataInternal() {
  }
}

