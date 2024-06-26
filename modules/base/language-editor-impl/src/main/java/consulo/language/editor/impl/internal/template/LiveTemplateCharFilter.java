/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.language.editor.impl.internal.template;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.completion.lookup.CharFilter;
import consulo.language.editor.completion.lookup.Lookup;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.template.LiveTemplateLookupElement;

/**
 * @author peter
 */
@ExtensionImpl(id = "liveTemplate", order = "first")
public class LiveTemplateCharFilter extends CharFilter {
  @Override
  public Result acceptChar(char c, int prefixLength, Lookup lookup) {
    LookupElement item = lookup.getCurrentItem();
    if (item instanceof LiveTemplateLookupElement && lookup.isCompletion()) {
      if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;

      if (c == ((LiveTemplateLookupElement)item).getTemplateShortcut()) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
      return Result.HIDE_LOOKUP;
    }
    if (item instanceof TemplateExpressionLookupElement) {
      if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;
      if (CodeInsightSettings.getInstance().SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS) {
        return null;
      }
      return Result.HIDE_LOOKUP;
    }

    return null;
  }
}
