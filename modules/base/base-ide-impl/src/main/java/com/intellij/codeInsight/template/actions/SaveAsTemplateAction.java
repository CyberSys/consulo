/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 20, 2002
 * Time: 5:04:04 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.template.actions;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.OffsetKey;
import com.intellij.codeInsight.completion.OffsetsInFile;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.impl.*;
import consulo.language.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import consulo.dataContext.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import consulo.document.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import consulo.document.RangeMarker;
import com.intellij.openapi.options.ShowSettingsUtil;
import consulo.language.psi.*;
import consulo.project.Project;
import consulo.document.util.TextRange;
import com.intellij.psi.*;
import consulo.language.psi.util.PsiElementFilter;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.LanguagePointerUtil;
import consulo.logging.Logger;
import consulo.component.util.pointer.NamedPointer;

import java.util.*;

public class SaveAsTemplateAction extends AnAction {

  private static final Logger LOG = Logger.getInstance(SaveAsTemplateAction.class);
  //FIXME [VISTALL] how remove this depend?
  private static final NamedPointer<Language> ourXmlLanguagePointer = LanguagePointerUtil.createPointer("XML");

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = Objects.requireNonNull(dataContext.getData(CommonDataKeys.EDITOR));
    PsiFile file = Objects.requireNonNull(dataContext.getData(CommonDataKeys.PSI_FILE));

    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final TextRange selection = new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
    PsiElement current = file.findElementAt(selection.getStartOffset());
    int startOffset = selection.getStartOffset();
    while (current instanceof PsiWhiteSpace) {
      current = current.getNextSibling();
      if (current == null) break;
      startOffset = current.getTextRange().getStartOffset();
    }

    if (startOffset >= selection.getEndOffset()) startOffset = selection.getStartOffset();

    final PsiElement[] psiElements = PsiTreeUtil.collectElements(file, new PsiElementFilter() {
      @Override
      public boolean isAccepted(PsiElement element) {
        return selection.contains(element.getTextRange()) && element.getReferences().length > 0;
      }
    });

    final Document document = EditorFactory.getInstance().createDocument(editor.getDocument().getText().
            substring(startOffset, selection.getEndOffset()));
    final boolean isXml = file.getLanguage().is(ourXmlLanguagePointer.get());
    final int offsetDelta = startOffset;
    new WriteCommandAction.Simple(project, (String)null) {
      @Override
      protected void run() throws Throwable {
        Map<RangeMarker, String> rangeToText = new HashMap<>();

        for (PsiElement element : psiElements) {
          for (PsiReference reference : element.getReferences()) {
            if (!(reference instanceof PsiQualifiedReference) || ((PsiQualifiedReference)reference).getQualifier() == null) {
              String canonicalText = reference.getCanonicalText();
              TextRange referenceRange = reference.getRangeInElement();
              final TextRange elementTextRange = element.getTextRange();
              LOG.assertTrue(elementTextRange != null, elementTextRange);
              final TextRange range = elementTextRange.cutOut(referenceRange).shiftRight(-offsetDelta);
              final String oldText = document.getText(range);
              // workaround for Java references: canonicalText contains generics, and we need to cut them off because otherwise
              // they will be duplicated
              int pos = canonicalText.indexOf('<');
              if (pos > 0 && !oldText.contains("<")) {
                canonicalText = canonicalText.substring(0, pos);
              }
              if (isXml) { //strip namespace prefixes
                pos = canonicalText.lastIndexOf(':');
                if (pos >= 0 && pos < canonicalText.length() - 1 && !oldText.contains(":")) {
                  canonicalText = canonicalText.substring(pos + 1);
                }
              }
              if (!canonicalText.equals(oldText)) {
                rangeToText.put(document.createRangeMarker(range), canonicalText);
              }
            }
          }
        }

        List<RangeMarker> markers = new ArrayList<>();
        for (RangeMarker m1 : rangeToText.keySet()) {
          boolean nested = false;
          for (RangeMarker m2 : rangeToText.keySet()) {
            if (m1 != m2 && m2.getStartOffset() <= m1.getStartOffset() && m1.getEndOffset() <= m2.getEndOffset()) {
              nested = true;
              break;
            }
          }

          if (!nested) {
            markers.add(m1);
          }
        }

        for (RangeMarker marker : markers) {
          final String value = rangeToText.get(marker);
          document.replaceString(marker.getStartOffset(), marker.getEndOffset(), value);
        }
      }
    }.execute();

    final TemplateImpl template = new TemplateImpl(TemplateListPanel.ABBREVIATION, document.getText(), TemplateSettings.USER_GROUP_NAME);
    template.setToReformat(true);

    OffsetKey startKey = OffsetKey.create("pivot");
    OffsetsInFile offsets = new OffsetsInFile(file);
    offsets.getOffsets().addOffset(startKey, startOffset);
    OffsetsInFile copy =
            TemplateManagerImpl.copyWithDummyIdentifier(offsets, editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd(), CompletionUtil.DUMMY_IDENTIFIER_TRIMMED);

    Set<TemplateContextType> applicable = TemplateManagerImpl.getApplicableContextTypes(TemplateActionContext.expanding(copy.getFile(), copy.getOffsets().getOffset(startKey)));

    for (TemplateContextType contextType : TemplateContextType.EP_NAME.getExtensionList()) {
      template.getTemplateContext().setEnabled(contextType, applicable.contains(contextType));
    }

    final LiveTemplatesConfigurable configurable = new LiveTemplatesConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> configurable.getTemplateListPanel().addTemplate(template));
  }

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    PsiFile file = dataContext.getData(CommonDataKeys.PSI_FILE);

    if (file == null || editor == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(editor.getSelectionModel().hasSelection());
    }
  }
}
