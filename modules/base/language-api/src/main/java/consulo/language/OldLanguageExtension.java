/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

/*
 * @author max
 */
package consulo.language;

import consulo.annotation.DeprecationInfo;
import consulo.application.extension.KeyedExtensionCollector;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@Deprecated(forRemoval = true)
@DeprecationInfo("Not worked with new extensions")
public class OldLanguageExtension<T> extends KeyedExtensionCollector<T, Language> {
  private final T myDefaultImplementation;
  private final /* non static!!! */ Key<T> IN_LANGUAGE_CACHE;

  public OldLanguageExtension(final String epName) {
    this(epName, null);
  }

  public OldLanguageExtension(final String epName, @Nullable final T defaultImplementation) {
    super(epName);
    myDefaultImplementation = defaultImplementation;
    IN_LANGUAGE_CACHE = Key.create("EXTENSIONS_IN_LANGUAGE_" + epName);
  }

  @Nonnull
  @Override
  protected String keyToString(@Nonnull final Language key) {
    return key.getID();
  }

  @SuppressWarnings("ConstantConditions")
  public T forLanguage(@Nonnull Language l) {
    T cached = l.getUserData(IN_LANGUAGE_CACHE);
    if (cached != null) return cached;

    List<T> extensions = forKey(l);
    T result;
    if (extensions.isEmpty()) {
      Language base = l.getBaseLanguage();
      result = base == null ? myDefaultImplementation : forLanguage(base);
    }
    else {
      result = extensions.get(0);
    }
    if (result == null) return null;
    result = l.putUserDataIfAbsent(IN_LANGUAGE_CACHE, result);
    return result;
  }

  @Nonnull
  public List<T> allForLanguage(Language l) {
    List<T> list = forKey(l);
    if (list.isEmpty()) {
      Language base = l.getBaseLanguage();
      if (base != null) {
        return allForLanguage(base);
      }
    }
    //if (l != Language.ANY) {
    //  final List<T> all = allForLanguage(Language.ANY);
    //  if (!all.isEmpty()) {
    //    if (list.isEmpty()) {
    //      return all;
    //    }
    //    list = new ArrayList<T>(list);
    //    list.addAll(all);
    //  }
    //}
    return list;
  }

  @Nonnull
  public List<T> allForLanguageOrAny(@Nonnull Language l) {
    List<T> providers = allForLanguage(l);
    if (l == Language.ANY) return providers;
    return ContainerUtil.concat(providers, allForLanguage(Language.ANY));
  }

  protected T getDefaultImplementation() {
    return myDefaultImplementation;
  }

  protected Key<T> getLanguageCache() {
    return IN_LANGUAGE_CACHE;
  }
}
