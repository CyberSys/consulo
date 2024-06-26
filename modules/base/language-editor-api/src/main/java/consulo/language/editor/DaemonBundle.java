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
package consulo.language.editor;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.component.util.localize.AbstractBundle;
import consulo.language.editor.localize.DaemonLocalize;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author max
 */
@Deprecated(forRemoval = true)
@DeprecationInfo("Use DaemonLocalize")
@MigratedExtensionsTo(DaemonLocalize.class)
public class DaemonBundle extends AbstractBundle{
  private static final DaemonBundle ourInstance = new DaemonBundle();

  private DaemonBundle() {
    super("consulo.language.editor.DaemonBundle");
  }

  public static String message(@PropertyKey(resourceBundle = "consulo.language.editor.DaemonBundle") String key) {
    return ourInstance.getMessage(key);
  }

  public static String message(@PropertyKey(resourceBundle = "consulo.language.editor.DaemonBundle") String key, Object... params) {
    return ourInstance.getMessage(key, params);
  }
}
