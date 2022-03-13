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
package com.intellij.codeInsight.completion;

import consulo.language.editor.completion.CompletionLocation;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.ide.impl.psi.statistics.StatisticsInfo;

/**
 * @author peter
 */
public class DefaultCompletionStatistician extends CompletionStatistician {

  @Override
  public StatisticsInfo serialize(final LookupElement element, final CompletionLocation location) {
    return new StatisticsInfo("completion#" + location.getCompletionParameters().getOriginalFile().getLanguage(), element.getLookupString());
  }
}