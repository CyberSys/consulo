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
package consulo.versionControlSystem;

import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.component.util.localize.AbstractBundle;
import consulo.annotation.DeprecationInfo;
import consulo.versionControlSystem.localize.VcsLocalize;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author lesya
 */
@Deprecated(forRemoval = true)
@DeprecationInfo("Use VcsLocalize")
@MigratedExtensionsTo(VcsLocalize.class)
public class VcsBundle extends AbstractBundle {
  private static final VcsBundle ourInstance = new VcsBundle();

  private VcsBundle() {
    super("consulo.versionControlSystem.VcsBundle");
  }

  public static String message(@PropertyKey(resourceBundle = "consulo.versionControlSystem.VcsBundle") String key) {
    return ourInstance.getMessage(key);
  }

  public static String message(@PropertyKey(resourceBundle = "consulo.versionControlSystem.VcsBundle") String key, Object... params) {
    return ourInstance.getMessage(key, params);
  }
}
