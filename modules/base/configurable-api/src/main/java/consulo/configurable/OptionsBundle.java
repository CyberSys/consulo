/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.configurable;

import consulo.annotation.DeprecationInfo;
import consulo.component.util.localize.AbstractBundle;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author lesya
 */
@Deprecated
@DeprecationInfo("Use consulo.configurable.localize.ConfigurableLocalize")
public class OptionsBundle extends AbstractBundle {
  private static final OptionsBundle INSTANCE = new OptionsBundle();

  public static final String PATH_TO_BUNDLE = "consulo.configurable.OptionsBundle";

  private OptionsBundle() {
    super(PATH_TO_BUNDLE);
  }

  public static String message(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}
