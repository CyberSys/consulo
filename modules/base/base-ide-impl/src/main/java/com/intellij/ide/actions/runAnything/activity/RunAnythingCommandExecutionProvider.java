// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import consulo.dataContext.DataContext;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RunAnythingCommandExecutionProvider extends RunAnythingCommandProvider {

  @Nullable
  @Override
  public String findMatchingValue(@Nonnull DataContext dataContext, @Nonnull String pattern) {
    return pattern;
  }

  @Nullable
  @Override
  public String getHelpGroupTitle() {
    return null;
  }
}