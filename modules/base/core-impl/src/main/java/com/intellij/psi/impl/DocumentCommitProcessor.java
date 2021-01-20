// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

public interface DocumentCommitProcessor {
  void commitSynchronously(@Nonnull Document document, @Nonnull Project project, @Nonnull PsiFile psiFile);

  void commitAsynchronously(@Nonnull Project project, @Nonnull Document document, @NonNls @Nonnull Object reason, @Nonnull ModalityState modality);
}
