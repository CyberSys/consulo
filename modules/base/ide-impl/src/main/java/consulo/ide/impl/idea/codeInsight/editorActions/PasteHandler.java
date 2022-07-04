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
package consulo.ide.impl.idea.codeInsight.editorActions;

import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.codeEditor.*;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.EditorActionManager;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.RangeMarker;
import consulo.document.ReadOnlyFragmentModificationException;
import consulo.document.util.DocumentUtil;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.ide.impl.idea.openapi.editor.EditorModificationUtil;
import consulo.ide.impl.idea.openapi.editor.actionSystem.EditorTextInsertHandler;
import consulo.ide.impl.idea.openapi.editor.actions.PasteAction;
import consulo.ide.impl.idea.openapi.util.text.StringUtil;
import consulo.ide.impl.idea.util.Producer;
import consulo.ide.impl.idea.util.text.CharArrayUtil;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.FormattingModelBuilder;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.CommonDataKeys;
import consulo.language.editor.action.CopyPastePreProcessor;
import consulo.language.impl.file.SingleRootFileViewProvider;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.ui.ex.PasteProvider;
import consulo.ui.ex.awt.CopyPasteManager;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.*;

public class PasteHandler extends EditorActionHandler implements EditorTextInsertHandler {
  private static final Logger LOG = Logger.getInstance(PasteHandler.class);

  private final EditorActionHandler myOriginalHandler;

  public PasteHandler(EditorActionHandler originalAction) {
    myOriginalHandler = originalAction;
  }

  @Override
  public void doExecute(final Editor editor, Caret caret, final DataContext dataContext) {
    assert caret == null : "Invocation of 'paste' operation for specific caret is not supported";
    execute(editor, dataContext, null);
  }

  @Override
  public void execute(final Editor editor, final DataContext dataContext, @Nullable final Producer<Transferable> producer) {
    final Transferable transferable = EditorModificationUtil.getContentsToPasteToEditor(producer);
    if (transferable == null) return;

    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;

    final Document document = editor.getDocument();
    if (!FileDocumentManager.getInstance().requestWriting(document, dataContext.getData(CommonDataKeys.PROJECT))) {
      return;
    }

    DataContext context = new DataContext() {
      @Override
      public Object getData(@NonNls Key dataId) {
        return PasteAction.TRANSFERABLE_PROVIDER == dataId ? new Producer<Transferable>() {
          @Nullable
          @Override
          public Transferable produce() {
            return transferable;
          }
        } : dataContext.getData(dataId);
      }
    };

    final Project project = editor.getProject();
    if (project == null || editor.isColumnMode() || editor.getCaretModel().getCaretCount() > 1) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, null, context);
      }
      return;
    }

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, null, context);
      }
      return;
    }

    DumbService.getInstance(project).setAlternativeResolveEnabled(true);
    document.startGuardedBlockChecking();
    try {
      for (PasteProvider provider : Application.get().getExtensionList(PasteProvider.class)) {
        if (provider.isPasteEnabled(context)) {
          provider.performPaste(context);
          return;
        }
      }
      doPaste(editor, project, file, document, transferable);
    }
    catch (ReadOnlyFragmentModificationException e) {
      EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(e);
    }
    finally {
      document.stopGuardedBlockChecking();
      DumbService.getInstance(project).setAlternativeResolveEnabled(false);
    }
  }

  private static void doPaste(final Editor editor,
                              final Project project,
                              final PsiFile file,
                              final Document document,
                              @Nonnull final Transferable content) {
    CopyPasteManager.getInstance().stopKillRings();

    String text = null;
    try {
      text = (String)content.getTransferData(DataFlavor.stringFlavor);
    }
    catch (Exception e) {
      editor.getComponent().getToolkit().beep();
    }
    if (text == null) return;

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    final Map<CopyPastePostProcessor, List<? extends TextBlockTransferableData>> extraData = new HashMap<CopyPastePostProcessor, List<? extends TextBlockTransferableData>>();
    final Collection<TextBlockTransferableData> allValues = new ArrayList<TextBlockTransferableData>();

    for (CopyPastePostProcessor<? extends TextBlockTransferableData> processor : CopyPastePostProcessor.EP_NAME.getExtensionList()) {
      List<? extends TextBlockTransferableData> data = processor.extractTransferableData(content);
      if (!data.isEmpty()) {
        extraData.put(processor, data);
        allValues.addAll(data);
      }
    }

    text = TextBlockTransferable.convertLineSeparators(editor, text, allValues);

    final CaretModel caretModel = editor.getCaretModel();
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int col = caretModel.getLogicalPosition().column;

    // There is a possible case that we want to perform paste while there is an active selection at the editor and caret is located
    // inside it (e.g. Ctrl+A is pressed while caret is not at the zero column). We want to insert the text at selection start column
    // then, hence, inserted block of text should be indented according to the selection start as well.
    final int blockIndentAnchorColumn;
    final int caretOffset = caretModel.getOffset();
    if (selectionModel.hasSelection() && caretOffset >= selectionModel.getSelectionStart()) {
      blockIndentAnchorColumn = editor.offsetToLogicalPosition(selectionModel.getSelectionStart()).column;
    }
    else {
      blockIndentAnchorColumn = col;
    }

    // We assume that EditorModificationUtil.insertStringAtCaret() is smart enough to remove currently selected text (if any).

    RawText rawText = RawText.fromTransferable(content);
    String newText = text;
    for (CopyPastePreProcessor preProcessor : CopyPastePreProcessor.EP_NAME.getExtensionList()) {
      newText = preProcessor.preprocessOnPaste(project, file, editor, newText, rawText);
    }
    int indentOptions = text.equals(newText) ? settings.REFORMAT_ON_PASTE : CodeInsightSettings.REFORMAT_BLOCK;
    text = newText;

    if (FormattingModelBuilder.forContext(file) == null && indentOptions != CodeInsightSettings.NO_REFORMAT) {
      indentOptions = CodeInsightSettings.INDENT_BLOCK;
    }

    final String _text = text;
    ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              @Override
              public void run() {
                EditorModificationUtil.insertStringAtCaret(editor, _text, false, true);
              }
            }
    );

    int length = text.length();
    int offset = caretModel.getOffset() - length;
    if (offset < 0) {
      length += offset;
      offset = 0;
    }
    final RangeMarker bounds = document.createRangeMarker(offset, offset + length);

    caretModel.moveToOffset(bounds.getEndOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    selectionModel.removeSelection();

    final Ref<Boolean> indented = new Ref<Boolean>(Boolean.FALSE);
    for (Map.Entry<CopyPastePostProcessor, List<? extends TextBlockTransferableData>> e : extraData.entrySet()) {
      //noinspection unchecked
      e.getKey().processTransferableData(project, editor, bounds, caretOffset, indented, e.getValue());
    }

    boolean pastedTextContainsWhiteSpacesOnly =
            CharArrayUtil.shiftForward(document.getCharsSequence(), bounds.getStartOffset(), " \n\t") >= bounds.getEndOffset();

    VirtualFile virtualFile = file.getVirtualFile();
    if (!pastedTextContainsWhiteSpacesOnly && (virtualFile == null || !SingleRootFileViewProvider.isTooLargeForIntelligence(virtualFile))) {
      final int indentOptions1 = indentOptions;

      ApplicationManager.getApplication().runWriteAction(
              new Runnable() {
                @Override
                public void run() {
                  PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
                  switch (indentOptions1) {
                    case CodeInsightSettings.INDENT_BLOCK:
                      if (!indented.get()) {
                        indentBlock(project, editor, bounds.getStartOffset(), bounds.getEndOffset(), blockIndentAnchorColumn);
                      }
                      break;

                    case CodeInsightSettings.INDENT_EACH_LINE:
                      if (!indented.get()) {
                        indentEachLine(project, editor, bounds.getStartOffset(), bounds.getEndOffset());
                      }
                      break;

                    case CodeInsightSettings.REFORMAT_BLOCK:
                      indentEachLine(project, editor, bounds.getStartOffset(), bounds.getEndOffset()); // this is needed for example when inserting a comment before method
                      reformatBlock(project, editor, bounds.getStartOffset(), bounds.getEndOffset());
                      break;
                  }
                }
              }
      );
    }

    if (bounds.isValid()) {
      caretModel.moveToOffset(bounds.getEndOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      selectionModel.removeSelection();
      editor.putUserData(EditorEx.LAST_PASTED_REGION, TextRange.create(bounds));
    }
  }

  static void indentBlock(Project project, Editor editor, final int startOffset, final int endOffset, int originalCaretCol) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitAllDocuments();
    final Document document = editor.getDocument();
    PsiFile file = documentManager.getPsiFile(document);
    if (file == null) {
      return;
    }

    if (FormattingModelBuilder.forContext(file) != null) {
      indentBlockWithFormatter(project, document, startOffset, endOffset, file);
    }
    else {
      indentPlainTextBlock(document, startOffset, endOffset, originalCaretCol);
    }
  }

  private static void indentEachLine(Project project, Editor editor, int startOffset, int endOffset) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final CharSequence text = editor.getDocument().getCharsSequence();
    if (startOffset > 0 && endOffset > startOffset + 1 && text.charAt(endOffset - 1) == '\n' && text.charAt(startOffset - 1) == '\n') {
      // There is a possible situation that pasted text ends by a line feed. We don't want to proceed it when a text is
      // pasted at the first line column.
      // Example:
      //    text to paste:
      //'if (true) {
      //'
      //    source:
      // if (true) {
      //     int i = 1;
      //     int j = 1;
      // }
      //
      //
      // We get the following on paste then:
      // if (true) {
      //     if (true) {
      //         int i = 1;
      //     int j = 1;
      // }
      //
      // We don't want line 'int i = 1;' to be indented here.
      endOffset--;
    }
    try {
      codeStyleManager.adjustLineIndent(file, new TextRange(startOffset, endOffset));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void reformatBlock(final Project project, final Editor editor, final int startOffset, final int endOffset) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    Runnable task = new Runnable() {
      @Override
      public void run() {
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        try {
          CodeStyleManager.getInstance(project).reformatRange(file, startOffset, endOffset, true);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };

    if (endOffset - startOffset > 1000) {
      DocumentUtil.executeInBulk(editor.getDocument(), true, task);
    }
    else {
      task.run();
    }
  }

  @SuppressWarnings("ForLoopThatDoesntUseLoopVariable")
  private static void indentPlainTextBlock(final Document document, final int startOffset, final int endOffset, final int indentLevel) {
    CharSequence chars = document.getCharsSequence();
    int spaceEnd = CharArrayUtil.shiftForward(chars, startOffset, " \t");
    int line = document.getLineNumber(startOffset);
    if (spaceEnd > endOffset || indentLevel <= 0 || line >= document.getLineCount() - 1 || chars.charAt(spaceEnd) == '\n') {
      return;
    }

    int linesToAdjustIndent = 0;
    for (int i = line + 1; i < document.getLineCount(); i++) {
      if (document.getLineStartOffset(i) >= endOffset) {
        break;
      }
      linesToAdjustIndent++;
    }

    String indentString = StringUtil.repeatSymbol(' ', indentLevel);

    for (; linesToAdjustIndent > 0; linesToAdjustIndent--) {
      int lineStartOffset = document.getLineStartOffset(++line);
      document.insertString(lineStartOffset, indentString);
    }
  }

  private static void indentBlockWithFormatter(Project project, Document document, int startOffset, int endOffset, PsiFile file) {

    // Algorithm: the main idea is to process the first line of the pasted block, adjust its indent if necessary, calculate indent
    // adjustment string and apply to each line of the pasted block starting from the second one.
    //
    // We differentiate the following possible states here:
    //   --- pasted block doesn't start new line, i.e. there are non-white space symbols before it at the first line.
    //      Example:
    //         old content [pasted line 1
    //                pasted line 2]
    //      Indent adjustment string is just the first line indent then.
    //
    //   --- pasted block starts with empty line(s)
    //      Example:
    //         old content [
    //            pasted line 1
    //            pasted line 2]
    //      We parse existing indents of the pasted block then, adjust its first non-blank line via formatter and adjust indent
    //      of subsequent pasted lines in order to preserve old indentation.
    //
    //   --- pasted block is located at the new line and starts with white space symbols.
    //       Example:
    //          [   pasted line 1
    //                 pasted line 2]
    //       We parse existing indents of the pasted block then, adjust its first line via formatter and adjust indent of the pasted lines
    //       starting from the second one in order to preserve old indentation.
    //
    //   --- pasted block is located at the new line but doesn't start with white space symbols.
    //       Example:
    //           [pasted line 1
    //         pasted line 2]
    //       We adjust the first line via formatter then and apply first line's indent to all subsequent pasted lines.

    CharSequence chars = document.getCharsSequence();
    final int firstLine = document.getLineNumber(startOffset);
    final int firstLineStart = document.getLineStartOffset(firstLine);

    // There is a possible case that we paste block that ends with new line that is empty or contains only white space symbols.
    // We want to preserve indent for the original document line where paste was performed.
    // Example:
    //   Original:
    //       if (test) {
    //   <caret>    }
    //
    //   Pasting: 'int i = 1;\n'
    //   Expected:
    //       if (test) {
    //           int i = 1;
    //       }
    boolean saveLastLineIndent = false;
    for (int i = endOffset - 1; i >= startOffset; i--) {
      final char c = chars.charAt(i);
      if (c == '\n') {
        saveLastLineIndent = true;
        break;
      }
      if (c != ' ' && c != '\t') {
        break;
      }
    }

    final int lastLine;
    if (saveLastLineIndent) {
      lastLine = document.getLineNumber(endOffset) - 1;
      // Remove white space symbols at the pasted text if any.
      int start = document.getLineStartOffset(lastLine + 1);
      if (start < endOffset) {
        int i = CharArrayUtil.shiftForward(chars, start, " \t");
        if (i > start) {
          i = Math.min(i, endOffset);
          document.deleteString(start, i);
        }
      }

      // Insert white space from the start line of the pasted block.
      int indentToKeepEndOffset = Math.min(startOffset, CharArrayUtil.shiftForward(chars, firstLineStart, " \t"));
      if (indentToKeepEndOffset > firstLineStart) {
        document.insertString(start, chars.subSequence(firstLineStart, indentToKeepEndOffset));
      }
    }
    else {
      lastLine = document.getLineNumber(endOffset);
    }

    final int i = CharArrayUtil.shiftBackward(chars, startOffset - 1, " \t");

    // Handle a situation when pasted block doesn't start a new line.
    if (chars.charAt(startOffset) != '\n' && i > 0 && chars.charAt(i) != '\n') {
      int firstNonWsOffset = CharArrayUtil.shiftForward(chars, firstLineStart, " \t");
      if (firstNonWsOffset > firstLineStart) {
        CharSequence toInsert = chars.subSequence(firstLineStart, firstNonWsOffset);
        for (int line = firstLine + 1; line <= lastLine; line++) {
          document.insertString(document.getLineStartOffset(line), toInsert);
        }
      }
      return;
    }

    // Sync document and PSI for correct formatting processing.
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    if (file == null) {
      return;
    }
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    final int j = CharArrayUtil.shiftForward(chars, startOffset, " \t\n");
    if (j >= endOffset) {
      // Pasted text contains white space/line feed symbols only, do nothing.
      return;
    }

    final int anchorLine = document.getLineNumber(j);
    final int anchorLineStart = document.getLineStartOffset(anchorLine);
    codeStyleManager.adjustLineIndent(file, j);

    // Handle situation when pasted block starts with non-white space symbols.
    if (anchorLine == firstLine && j == startOffset) {
      int indentOffset = CharArrayUtil.shiftForward(chars, firstLineStart, " \t");
      if (indentOffset > firstLineStart) {
        CharSequence toInsert = chars.subSequence(firstLineStart, indentOffset);
        for (int line = firstLine + 1; line <= lastLine; line++) {
          document.insertString(document.getLineStartOffset(line), toInsert);
        }
      }
      return;
    }

    // Handle situation when pasted block starts from white space symbols. Assume that the pasted text started at the line start,
    // i.e. correct indentation level is stored at the blocks structure.
    final int firstNonWsOffset = CharArrayUtil.shiftForward(chars, anchorLineStart, " \t");
    final int diff = firstNonWsOffset - j;
    if (diff == 0) {
      return;
    }
    if (diff > 0) {
      CharSequence toInsert = chars.subSequence(anchorLineStart, anchorLineStart + diff);
      for (int line = anchorLine + 1; line <= lastLine; line++) {
        document.insertString(document.getLineStartOffset(line), toInsert);
      }
      return;
    }

    // We've pasted text to the non-first column and exact white space between the line start and caret position on the moment of paste
    // has been removed by formatter during 'adjust line indent'
    // Example:
    //       copied text:
    //                 '   line1
    //                       line2'
    //       after paste:
    //          line start -> '   I   line1
    //                              line2' (I - caret position during 'paste')
    //       formatter removed white space between the line start and caret position, so, current document state is:
    //                        '   line1
    //                              line2'
    if (anchorLine == firstLine && -diff == startOffset - firstLineStart) {
      return;
    }
    if (anchorLine != firstLine || -diff > startOffset - firstLineStart) {
      final int desiredSymbolsToRemove;
      if (anchorLine == firstLine) {
        desiredSymbolsToRemove = -diff - (startOffset - firstLineStart);
      }
      else {
        desiredSymbolsToRemove = -diff;
      }

      for (int line = anchorLine + 1; line <= lastLine; line++) {
        int currentLineStart = document.getLineStartOffset(line);
        int currentLineIndentOffset = CharArrayUtil.shiftForward(chars, currentLineStart, " \t");
        int symbolsToRemove = Math.min(currentLineIndentOffset - currentLineStart, desiredSymbolsToRemove);
        if (symbolsToRemove > 0) {
          document.deleteString(currentLineStart, currentLineStart + symbolsToRemove);
        }
      }
    }
    else {
      CharSequence toInsert = chars.subSequence(anchorLineStart, diff + startOffset);
      for (int line = anchorLine + 1; line <= lastLine; line++) {
        document.insertString(document.getLineStartOffset(line), toInsert);
      }
    }
  }
}
