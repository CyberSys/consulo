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
import consulo.language.editor.localize.CodeInsightLocalize;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author max
 */
@Deprecated(forRemoval = true)
@DeprecationInfo("Use CodeInsightLocalize")
@MigratedExtensionsTo(CodeInsightLocalize.class)
public class CodeInsightBundle extends AbstractBundle {
  private static final CodeInsightBundle ourInstance = new CodeInsightBundle();

  private CodeInsightBundle() {
    super("consulo.language.editor.CodeInsightBundle");
  }

  public static String message(@PropertyKey(resourceBundle = "consulo.language.editor.CodeInsightBundle") String key) {
    return ourInstance.getMessage(key);
  }

  public static String message(@PropertyKey(resourceBundle = "consulo.language.editor.CodeInsightBundle") String key, Object... params) {
    return ourInstance.getMessage(key, params);
  }
}
