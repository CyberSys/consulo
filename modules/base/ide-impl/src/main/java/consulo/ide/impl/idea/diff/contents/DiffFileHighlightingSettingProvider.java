/*
 * Copyright 2013-2022 consulo.io
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
package consulo.ide.impl.idea.diff.contents;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.DefaultHighlightingSettingProvider;
import consulo.language.editor.FileHighlightingSetting;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
* @author VISTALL
* @since 22-Jun-22
*/
@ExtensionImpl
public class DiffFileHighlightingSettingProvider extends DefaultHighlightingSettingProvider {
  @Nullable
  @Override
  public FileHighlightingSetting getDefaultSetting(@Nonnull Project project, @Nonnull VirtualFile file) {
    if (!DiffPsiFileSupport.isDiffFile(file)) return null;
    return FileHighlightingSetting.SKIP_INSPECTION;
  }
}
