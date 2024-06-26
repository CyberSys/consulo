/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.ide.impl.idea.util.indexing.impl;

import consulo.util.lang.Comparing;
import consulo.index.io.internal.DebugAssertions;
import consulo.util.lang.SystemProperties;
import consulo.index.io.StorageException;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

//@ApiStatus.Experimental
public class MapInputDataDiffBuilder<Key, Value> extends InputDataDiffBuilder<Key, Value> {
  private static final boolean ourDiffUpdateEnabled = SystemProperties.getBooleanProperty("idea.disable.diff.index.update", true);

  private final Map<Key, Value> myMap;

  public MapInputDataDiffBuilder(int inputId, @Nullable Map<Key, Value> map) {
    super(inputId);
    myMap = map == null ? Collections.emptyMap() : map;
  }

  @Override
  public boolean differentiate(@Nonnull Map<Key, Value> newData,
                               @Nonnull KeyValueUpdateProcessor<? super Key, ? super Value> addProcessor,
                               @Nonnull KeyValueUpdateProcessor<? super Key, ? super Value> updateProcessor,
                               @Nonnull RemovedKeyProcessor<? super Key> removeProcessor) throws StorageException {
    if (ourDiffUpdateEnabled) {
      if (myMap.isEmpty()) {
        EmptyInputDataDiffBuilder.processKeys(newData, addProcessor, myInputId);
      }
      else if (newData.isEmpty()) {
        processAllKeysAsDeleted(removeProcessor);
      }
      else {
        int added = 0;
        int removed = 0;

        for (Map.Entry<Key, Value> e : myMap.entrySet()) {
          final Key key = e.getKey();
          final Value newValue = newData.get(key);
          if (!Comparing.equal(e.getValue(), newValue) || (newValue == null && !newData.containsKey(key))) {
            if (!newData.containsKey(key)) {
              removeProcessor.process(key, myInputId);
              removed++;
            }
            else {
              updateProcessor.process(key, newValue, myInputId);
              added++;
              removed++;
            }
          }
        }

        for (Map.Entry<Key, Value> e : newData.entrySet()) {
          final Key key = e.getKey();
          if (!myMap.containsKey(key)) {
            addProcessor.process(key, e.getValue(), myInputId);
            added++;
          }
        }

        incrementalAdditions.addAndGet(added);
        incrementalRemovals.addAndGet(removed);
        int totalRequests = requests.incrementAndGet();
        totalRemovals.addAndGet(myMap.size());
        totalAdditions.addAndGet(newData.size());

        if ((totalRequests & 0xFFFF) == 0 && DebugAssertions.DEBUG) {
          Logger.getInstance(getClass()).info("Incremental index diff update:" + requests +
                                              ", removals:" + totalRemovals + "->" + incrementalRemovals +
                                              ", additions:" + totalAdditions + "->" + incrementalAdditions +
                                              ", no op changes:" + noopModifications);
        }

        if (added == 0 && removed == 0) {
          noopModifications.incrementAndGet();
          return false;
        }
      }
    }
    else {
      CollectionInputDataDiffBuilder.differentiateWithKeySeq(myMap.keySet(), newData, myInputId, addProcessor, removeProcessor);
    }
    return true;
  }

  private void processAllKeysAsDeleted(final RemovedKeyProcessor<? super Key> removeProcessor) throws StorageException {
    for (Key key : myMap.keySet()) {
      removeProcessor.process(key, myInputId);
    }
  }

  private static final AtomicInteger requests = new AtomicInteger();
  private static final AtomicInteger totalRemovals = new AtomicInteger();
  private static final AtomicInteger totalAdditions = new AtomicInteger();
  private static final AtomicInteger incrementalRemovals = new AtomicInteger();
  private static final AtomicInteger incrementalAdditions = new AtomicInteger();
  private static final AtomicInteger noopModifications = new AtomicInteger();
}
