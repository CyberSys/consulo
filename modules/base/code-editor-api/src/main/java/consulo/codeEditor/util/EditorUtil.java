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
package consulo.codeEditor.util;

import consulo.codeEditor.*;
import consulo.colorScheme.TextAttributes;
import consulo.document.Document;
import consulo.document.util.DocumentUtil;
import consulo.util.lang.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;

/**
 * @author VISTALL
 * @since 12-Mar-22
 */
public class EditorUtil {
  public static int calcRelativeCaretPosition(@Nonnull Editor editor) {
    int caretY = editor.getCaretModel().getVisualPosition().line * editor.getLineHeight();
    int viewAreaPosition = editor.getScrollingModel().getVisibleAreaOnScrollingFinished().y;
    return caretY - viewAreaPosition;
  }

  public static boolean isAtLineEnd(@Nonnull Editor editor, int offset) {
    Document document = editor.getDocument();
    if (offset < 0 || offset > document.getTextLength()) {
      return false;
    }
    int line = document.getLineNumber(offset);
    return offset == document.getLineEndOffset(line);
  }

  /**
   * Number of virtual soft wrap introduced lines on a current logical line before the visual position that corresponds
   * to the current logical position.
   *
   * @see LogicalPosition#softWrapLinesOnCurrentLogicalLine
   */
  public static int getSoftWrapCountAfterLineStart(@Nonnull Editor editor, @Nonnull LogicalPosition position) {
    if (position.visualPositionAware) {
      return position.softWrapLinesOnCurrentLogicalLine;
    }
    int startOffset = editor.getDocument().getLineStartOffset(position.line);
    int endOffset = editor.logicalPositionToOffset(position);
    return editor.getSoftWrapModel().getSoftWrapsForRange(startOffset, endOffset).size();
  }

  /**
   * Performs inlay-aware conversion of offset to visual position in editor. If there are inlays at given position, their
   * 'related to preceding text' property will be taken account to determine resulting position. Specifically, resulting position will
   * match caret's visual position if it's moved to the given offset using {@link Caret#moveToOffset(int)} call.
   * <p>
   * NOTE: if editor is an {@link EditorWindow}, corresponding offset is treated as an offset in injected editor, but returned position
   * is always related to host editor.
   *
   * @see Inlay#isRelatedToPrecedingText()
   */
  @Nonnull
  public static VisualPosition inlayAwareOffsetToVisualPosition(@Nonnull Editor editor, int offset) {
    LogicalPosition logicalPosition = editor.offsetToLogicalPosition(offset);
    if (editor instanceof InjectedEditor) {
      logicalPosition = ((InjectedEditor)editor).injectedToHost(logicalPosition);
      editor = ((InjectedEditor)editor).getDelegate();
    }
    VisualPosition pos = editor.logicalToVisualPosition(logicalPosition);
    Inlay inlay;
    while ((inlay = editor.getInlayModel().getInlineElementAt(pos)) != null) {
      if (inlay.isRelatedToPrecedingText()) break;
      pos = new VisualPosition(pos.line, pos.column + 1);
    }
    return pos;
  }

  public static boolean attributesImpactFontStyleOrColor(@Nullable TextAttributes attributes) {
    return attributes == TextAttributes.ERASE_MARKER || (attributes != null && (attributes.getFontType() != Font.PLAIN || attributes.getForegroundColor() != null));
  }

  public static int getTabSize(@Nonnull Editor editor) {
    return editor.getSettings().getTabSize(editor.getProject());
  }

  public static int getTotalInlaysHeight(@Nonnull List<? extends Inlay> inlays) {
    int sum = 0;
    for (Inlay inlay : inlays) {
      sum += inlay.getHeightInPixels();
    }
    return sum;
  }

  public static boolean inVirtualSpace(@Nonnull Editor editor, @Nonnull LogicalPosition logicalPosition) {
    return !editor.offsetToLogicalPosition(editor.logicalPositionToOffset(logicalPosition)).equals(logicalPosition);
  }

  /**
   * Delegates to the {@link #calcSurroundingRange(Editor, VisualPosition, VisualPosition)} with the
   * {@link CaretModel#getVisualPosition() caret visual position} as an argument.
   *
   * @param editor target editor
   * @return surrounding logical positions
   * @see #calcSurroundingRange(Editor, VisualPosition, VisualPosition)
   */
  public static Pair<LogicalPosition, LogicalPosition> calcCaretLineRange(@Nonnull Editor editor) {
    return calcSurroundingRange(editor, editor.getCaretModel().getVisualPosition(), editor.getCaretModel().getVisualPosition());
  }

  public static Pair<LogicalPosition, LogicalPosition> calcCaretLineRange(@Nonnull Caret caret) {
    return calcSurroundingRange(caret.getEditor(), caret.getVisualPosition(), caret.getVisualPosition());
  }

  /**
   * Calculates logical positions that surround given visual positions and conform to the following criteria:
   * <pre>
   * <ul>
   *   <li>located at the start or the end of the visual line;</li>
   *   <li>doesn't have soft wrap at the target offset;</li>
   * </ul>
   * </pre>
   * Example:
   * <pre>
   *   first line [soft-wrap] some [start-position] text [end-position] [fold-start] fold line 1
   *   fold line 2
   *   fold line 3[fold-end] [soft-wrap] end text
   * </pre>
   * The very first and the last positions will be returned here.
   *
   * @param editor target editor to use
   * @param start  target start coordinate
   * @param end    target end coordinate
   * @return pair of the closest surrounding non-soft-wrapped logical positions for the visual line start and end
   * @see #getNotFoldedLineStartOffset(Editor, int)
   * @see #getNotFoldedLineEndOffset(Editor, int)
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  public static Pair<LogicalPosition, LogicalPosition> calcSurroundingRange(@Nonnull Editor editor,
                                                                            @Nonnull VisualPosition start,
                                                                            @Nonnull VisualPosition end) {
    final Document document = editor.getDocument();
    final FoldingModel foldingModel = editor.getFoldingModel();

    LogicalPosition first = editor.visualToLogicalPosition(new VisualPosition(start.line, 0));
    for (int line = first.line, offset = document.getLineStartOffset(line); offset >= 0; offset = document.getLineStartOffset(line)) {
      final FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(offset);
      if (foldRegion == null) {
        first = new LogicalPosition(line, 0);
        break;
      }
      final int foldEndLine = document.getLineNumber(foldRegion.getStartOffset());
      if (foldEndLine <= line) {
        first = new LogicalPosition(line, 0);
        break;
      }
      line = foldEndLine;
    }


    LogicalPosition second = editor.visualToLogicalPosition(new VisualPosition(end.line, 0));
    for (int line = second.line, offset = document.getLineEndOffset(line); offset <= document.getTextLength();
         offset = document.getLineEndOffset(line)) {
      final FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(offset);
      if (foldRegion == null) {
        second = new LogicalPosition(line + 1, 0);
        break;
      }
      final int foldEndLine = document.getLineNumber(foldRegion.getEndOffset());
      if (foldEndLine <= line) {
        second = new LogicalPosition(line + 1, 0);
        break;
      }
      line = foldEndLine;
    }

    if (second.line >= document.getLineCount()) {
      second = editor.offsetToLogicalPosition(document.getTextLength());
    }
    return Pair.create(first, second);
  }


  /**
   * Finds the end offset of visual line at which given offset is located, not taking soft wraps into account.
   */
  public static int getNotFoldedLineEndOffset(@Nonnull Editor editor, int offset) {
    while (true) {
      offset = getLineEndOffset(offset, editor.getDocument());
      FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(offset);
      if (foldRegion == null || foldRegion.getEndOffset() <= offset) {
        break;
      }
      offset = foldRegion.getEndOffset();
    }
    return offset;
  }

  private static int getLineEndOffset(int offset, Document document) {
    if (offset >= document.getTextLength()) {
      return offset;
    }
    int lineNumber = document.getLineNumber(offset);
    return document.getLineEndOffset(lineNumber);
  }

  /**
   * Finds the start offset of visual line at which given offset is located, not taking soft wraps into account.
   */
  public static int getNotFoldedLineStartOffset(@Nonnull Editor editor, int offset) {
    while (true) {
      offset = DocumentUtil.getLineStartOffset(offset, editor.getDocument());
      FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(offset - 1);
      if (foldRegion == null || foldRegion.getStartOffset() >= offset) {
        break;
      }
      offset = foldRegion.getStartOffset();
    }
    return offset;
  }

  /**
   * Maps {@code y} to a logical line in editor (in the same way as {@link #yPositionToLogicalLine(Editor, int)} does), except that for
   * coordinates, corresponding to block inlay or custom fold region locations, {@code -1} is returned.
   */
  public static int yToLogicalLineNoCustomRenderers(@Nonnull Editor editor, int y) {
    int visualLine = editor.yToVisualLine(y);
    int visualLineStartY = editor.visualLineToY(visualLine);
    if (y < visualLineStartY || y >= visualLineStartY + editor.getLineHeight()) return -1;
    int line = editor.visualToLogicalPosition(new VisualPosition(visualLine, 0)).line;
    Document document = editor.getDocument();
    if (line < document.getLineCount()) {
      int lineStartOffset = document.getLineStartOffset(line);
      FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(lineStartOffset);
      if (foldRegion instanceof CustomFoldRegion) {
        return -1;
      }
    }
    return line;
  }
}
