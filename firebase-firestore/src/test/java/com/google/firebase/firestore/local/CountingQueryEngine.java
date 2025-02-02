// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.local;

import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.Overlay;
import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;

/**
 * A test-only QueryEngine that forwards all API calls and exposes the number of documents and
 * mutations read.
 */
class CountingQueryEngine extends QueryEngine {
  private final QueryEngine queryEngine;

  private final int[] overlaysReadByCollection = new int[] {0};
  private final int[] overlaysReadByKey = new int[] {0};
  private final int[] documentsReadByCollection = new int[] {0};
  private final int[] documentsReadByKey = new int[] {0};

  CountingQueryEngine(QueryEngine queryEngine) {
    this.queryEngine = queryEngine;
  }

  void resetCounts() {
    overlaysReadByCollection[0] = 0;
    overlaysReadByKey[0] = 0;
    documentsReadByCollection[0] = 0;
    documentsReadByKey[0] = 0;
  }

  @Override
  public void initialize(LocalDocumentsView localDocuments, IndexManager indexManager) {
    LocalDocumentsView wrappedView =
        new LocalDocumentsView(
            wrapRemoteDocumentCache(localDocuments.getRemoteDocumentCache()),
            localDocuments.getMutationQueue(),
            wrapOverlayCache(localDocuments.getDocumentOverlayCache()),
            indexManager);
    queryEngine.initialize(wrappedView, indexManager);
  }

  @Override
  public ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query,
      SnapshotVersion lastLimboFreeSnapshotVersion,
      ImmutableSortedSet<DocumentKey> remoteKeys) {
    return queryEngine.getDocumentsMatchingQuery(query, lastLimboFreeSnapshotVersion, remoteKeys);
  }

  /** Returns the query engine that is used as the backing implementation. */
  QueryEngine getSubject() {
    return queryEngine;
  }

  /**
   * Returns the number of documents returned by the RemoteDocumentCache's `getAll()` API (since the
   * last call to `resetCounts()`)
   */
  int getDocumentsReadByCollection() {
    return documentsReadByCollection[0];
  }

  /**
   * Returns the number of documents returned by the RemoteDocumentCache's `getEntry()` and
   * `getEntries()` APIs (since the last call to `resetCounts()`)
   */
  int getDocumentsReadByKey() {
    return documentsReadByKey[0];
  }

  /**
   * Returns the number of mutations returned by the OverlayCache's `getOverlays()` API (since the
   * last call to `resetCounts()`)
   */
  int getOverlaysReadByCollection() {
    return overlaysReadByCollection[0];
  }

  /**
   * Returns the number of mutations returned by the OverlayCache's `getOverlay()` API (since the
   * last call to `resetCounts()`)
   */
  int getOverlaysReadByKey() {
    return overlaysReadByKey[0];
  }

  private RemoteDocumentCache wrapRemoteDocumentCache(RemoteDocumentCache subject) {
    return new RemoteDocumentCache() {
      @Override
      public void setIndexManager(IndexManager indexManager) {
        // Not implemented.
      }

      @Override
      public void add(MutableDocument document, SnapshotVersion readTime) {
        subject.add(document, readTime);
      }

      @Override
      public void removeAll(Collection<DocumentKey> keys) {
        subject.removeAll(keys);
      }

      @Override
      public MutableDocument get(DocumentKey documentKey) {
        MutableDocument result = subject.get(documentKey);
        documentsReadByKey[0] += result.isValidDocument() ? 1 : 0;
        return result;
      }

      @Override
      public Map<DocumentKey, MutableDocument> getAll(Iterable<DocumentKey> documentKeys) {
        Map<DocumentKey, MutableDocument> result = subject.getAll(documentKeys);
        for (MutableDocument document : result.values()) {
          documentsReadByKey[0] += document.isValidDocument() ? 1 : 0;
        }
        return result;
      }

      @Override
      public Map<DocumentKey, MutableDocument> getAll(
          String collectionGroup, IndexOffset offset, int limit) {
        Map<DocumentKey, MutableDocument> result = subject.getAll(collectionGroup, offset, limit);
        documentsReadByCollection[0] += result.size();

        return result;
      }

      @Override
      public Map<DocumentKey, MutableDocument> getAll(ResourcePath collection, IndexOffset offset) {
        Map<DocumentKey, MutableDocument> result = subject.getAll(collection, offset);
        documentsReadByCollection[0] += result.size();
        return result;
      }
    };
  }

  private DocumentOverlayCache wrapOverlayCache(DocumentOverlayCache subject) {
    return new DocumentOverlayCache() {
      @Nullable
      @Override
      public Overlay getOverlay(DocumentKey key) {
        ++overlaysReadByKey[0];
        return subject.getOverlay(key);
      }

      public Map<DocumentKey, Overlay> getOverlays(SortedSet<DocumentKey> keys) {
        overlaysReadByKey[0] += keys.size();
        return subject.getOverlays(keys);
      }

      @Override
      public void saveOverlays(int largestBatchId, Map<DocumentKey, Mutation> overlays) {
        subject.saveOverlays(largestBatchId, overlays);
      }

      @Override
      public void removeOverlaysForBatchId(int batchId) {
        subject.removeOverlaysForBatchId(batchId);
      }

      @Override
      public Map<DocumentKey, Overlay> getOverlays(ResourcePath collection, int sinceBatchId) {
        Map<DocumentKey, Overlay> result = subject.getOverlays(collection, sinceBatchId);
        overlaysReadByCollection[0] += result.size();
        return result;
      }

      @Override
      public Map<DocumentKey, Overlay> getOverlays(
          String collectionGroup, int sinceBatchId, int count) {
        Map<DocumentKey, Overlay> result =
            subject.getOverlays(collectionGroup, sinceBatchId, count);
        overlaysReadByCollection[0] += result.size();
        return result;
      }
    };
  }
}
