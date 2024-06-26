/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.util;

import consulo.util.lang.Comparing;

public class UnorderedPair<T> {
  public final T first;
  public final T second;

  public UnorderedPair(T first, T second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public int hashCode() {
    int hc1 = first == null ? 0 : first.hashCode();
    int hc2 = second == null ? 0 : second.hashCode();
    return hc1 * hc1 + hc2 * hc2;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (obj.getClass() != getClass()) return false;

    final UnorderedPair other = (UnorderedPair)obj;
    if (Comparing.equal(other.first, first) && Comparing.equal(other.second, second)) return true;
    if (Comparing.equal(other.first, second) && Comparing.equal(other.second, first)) return true;
    return false;
  }

  @Override
  public String toString() {
    return "<" + first + ", " + second + '>';
  }
}
