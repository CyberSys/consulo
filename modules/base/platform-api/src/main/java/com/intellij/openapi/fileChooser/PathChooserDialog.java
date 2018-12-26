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
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import consulo.ui.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author Roman Shevchenko
 * @since 13.02.2012
 */
public interface PathChooserDialog {
  Key<Boolean> PREFER_LAST_OVER_EXPLICIT = Key.create("prefer.last.over.explicit");
  Key<Boolean> NATIVE_MAC_CHOOSER_SHOW_HIDDEN_FILES = Key.create("native.mac.chooser.show.hidden.files");

  @Deprecated
  default void choose(@Nullable VirtualFile toSelect, @Nonnull final Consumer<List<VirtualFile>> callback) {
    chooseAsync(toSelect).doWhenDone((f) -> callback.consume(Arrays.asList(f))).getResultSync();
  }

  @Nonnull
  @RequiredUIAccess
  AsyncResult<VirtualFile[]> chooseAsync(@Nullable VirtualFile toSelect);
}
