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

package consulo.ide.impl.idea.codeInsight.completion.actions;

import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.impl.internal.action.BaseCodeInsightAction;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.codeEditor.Editor;
import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import javax.annotation.Nonnull;
import consulo.ui.annotation.RequiredUIAccess;

public class HippieBackwardCompletionAction extends BaseCodeInsightAction implements DumbAware {
  public HippieBackwardCompletionAction() {
    setEnabledInModalContext(true);
  }

  @RequiredUIAccess
  @Override
  public void actionPerformedImpl(@Nonnull Project project, Editor editor) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.hippie");
    super.actionPerformedImpl(project, editor);
  }

  @Nonnull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new HippieWordCompletionHandler(false);
  }

  @Override
  protected boolean isValidForLookup() {
    return true;
  }
}
