/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.index.io.storage;

public abstract class CapacityAllocationPolicy {
  public abstract int calculateCapacity(int requiredLength);

  public static final CapacityAllocationPolicy FIXED = new CapacityAllocationPolicy() {
    @Override
    public int calculateCapacity(int requiredLength) {
      return requiredLength;
    }
  };

  public static final CapacityAllocationPolicy FIVE_PERCENT_FOR_GROWTH = new CapacityAllocationPolicy() {
    @Override
    public int calculateCapacity(int requiredLength) {
      return Math.min((int)(requiredLength * 1.05), (requiredLength / 1024 + 1) * 1024);
    }
  };

  public static final CapacityAllocationPolicy DEFAULT = new CapacityAllocationPolicy() {
    @Override
    public int calculateCapacity(int requiredLength) {
      return Math.max(64, Math.min(Integer.highestOneBit(requiredLength * 3 / 2) << 1, (requiredLength / 1024 + 1) * 1024));
    }
  };

  public static final CapacityAllocationPolicy REASONABLY_SMALL = new CapacityAllocationPolicy() {
    @Override
    public int calculateCapacity(int requiredLength) {   // 20% for growth
      return Math.max(8, Math.min((int)(requiredLength * 1.2), (requiredLength / 1024 + 1) * 1024));
    }
  };
}
