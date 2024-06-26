// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package consulo.ide.impl.idea.ide.todo.nodes;

import consulo.annotation.access.RequiredReadAction;
import consulo.ui.ex.tree.PresentationData;
import consulo.ide.impl.idea.ide.todo.HighlightedRegionProvider;
import consulo.ide.impl.idea.ide.todo.SmartTodoItemPointer;
import consulo.ide.impl.idea.ide.todo.TodoTreeBuilder;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.language.editor.impl.internal.highlight.TodoAttributesUtil;
import consulo.logging.Logger;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.codeEditor.EditorHighlighter;
import consulo.codeEditor.HighlighterIterator;
import consulo.colorScheme.TextAttributes;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.document.util.TextRange;
import consulo.language.psi.search.TodoItem;
import consulo.ui.ex.awt.HighlightedRegion;
import consulo.ide.impl.idea.util.text.CharArrayUtil;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class TodoItemNode extends BaseToDoNode<SmartTodoItemPointer> implements HighlightedRegionProvider {
  private static final Logger LOG = Logger.getInstance(TodoItemNode.class);

  private final ArrayList<HighlightedRegion> myHighlightedRegions;
  private final List<HighlightedRegionProvider> myAdditionalLines;

  public TodoItemNode(Project project, @Nonnull SmartTodoItemPointer value, TodoTreeBuilder builder) {
    super(project, value, builder);
    RangeMarker rangeMarker = getValue().getRangeMarker();
    LOG.assertTrue(rangeMarker.isValid());

    myHighlightedRegions = new ArrayList<>();
    myAdditionalLines = new ArrayList<>();
  }

  @Override
  public boolean contains(Object element) {
    return canRepresent(element);
  }

  @Override
  public boolean canRepresent(Object element) {
    SmartTodoItemPointer value = getValue();
    TodoItem item = value != null ? value.getTodoItem() : null;
    return Comparing.equal(item, element);
  }

  @Override
  public int getFileCount(final SmartTodoItemPointer val) {
    return 1;
  }

  @Override
  public int getTodoItemCount(final SmartTodoItemPointer val) {
    return 1;
  }

  @Override
  public ArrayList<HighlightedRegion> getHighlightedRegions() {
    return myHighlightedRegions;
  }

  @RequiredReadAction
  @Override
  @Nonnull
  public Collection<AbstractTreeNode> getChildren() {
    return Collections.emptyList();
  }

  @Override
  public void update(@Nonnull PresentationData presentation) {
    SmartTodoItemPointer todoItemPointer = getValue();
    assert todoItemPointer != null;
    TodoItem todoItem = todoItemPointer.getTodoItem();
    RangeMarker myRangeMarker = todoItemPointer.getRangeMarker();
    if (!todoItem.getFile().isValid() || !myRangeMarker.isValid() || myRangeMarker.getStartOffset() == myRangeMarker.getEndOffset()) {
      myRangeMarker.dispose();
      setValue(null);
      return;
    }

    myHighlightedRegions.clear();
    myAdditionalLines.clear();

    // Update name

    Document document = todoItemPointer.getDocument();
    CharSequence chars = document.getCharsSequence();
    int startOffset = myRangeMarker.getStartOffset();
    int endOffset = myRangeMarker.getEndOffset();
    int lineNumber = document.getLineNumber(startOffset);
    int lineStartOffset = document.getLineStartOffset(lineNumber);
    int columnNumber = startOffset - lineStartOffset;

    // skip all white space characters

    while (lineStartOffset < document.getTextLength() && (chars.charAt(lineStartOffset) == '\t' || chars.charAt(lineStartOffset) == ' ')) {
      lineStartOffset++;
    }

    int lineEndOffset = document.getLineEndOffset(lineNumber);

    String lineColumnPrefix = "(" + (lineNumber + 1) + ", " + (columnNumber + 1) + ") ";

    String highlightedText = chars.subSequence(lineStartOffset, Math.min(lineEndOffset, chars.length())).toString();

    String newName = lineColumnPrefix + highlightedText;

    // Update icon

    Image newIcon = todoItem.getPattern().getAttributes().getIcon();

    // Update highlighted regions

    myHighlightedRegions.clear();
    EditorHighlighter highlighter = myBuilder.getHighlighter(todoItem.getFile(), document);
    collectHighlights(myHighlightedRegions, highlighter, lineStartOffset, lineEndOffset, lineColumnPrefix.length());
    TextAttributes attributes = TodoAttributesUtil.getTextAttributes(todoItem.getPattern().getAttributes());
    myHighlightedRegions.add(new HighlightedRegion(lineColumnPrefix.length() + startOffset - lineStartOffset, lineColumnPrefix.length() + endOffset - lineStartOffset, attributes));

    //

    presentation.setPresentableText(newName);
    presentation.setIcon(newIcon);

    for (RangeMarker additionalMarker : todoItemPointer.getAdditionalRangeMarkers()) {
      if (!additionalMarker.isValid()) break;
      ArrayList<HighlightedRegion> highlights = new ArrayList<>();
      int lineNum = document.getLineNumber(additionalMarker.getStartOffset());
      int lineStart = document.getLineStartOffset(lineNum);
      int lineEnd = document.getLineEndOffset(lineNum);
      int lineStartNonWs = CharArrayUtil.shiftForward(chars, lineStart, " \t");
      if (lineStartNonWs > additionalMarker.getStartOffset() || lineEnd < additionalMarker.getEndOffset()) {
        // can happen for an invalid (obsolete) node, tree implementation can call this method for such a node
        break;
      }
      collectHighlights(highlights, highlighter, lineStartNonWs, lineEnd, 0);
      highlights.add(new HighlightedRegion(additionalMarker.getStartOffset() - lineStartNonWs, additionalMarker.getEndOffset() - lineStartNonWs, attributes));
      myAdditionalLines.add(new AdditionalTodoLine(document.getText(new TextRange(lineStartNonWs, lineEnd)), highlights));
    }
  }

  private static void collectHighlights(@Nonnull List<? super HighlightedRegion> highlights, @Nonnull EditorHighlighter highlighter, int startOffset, int endOffset, int highlightOffsetShift) {
    HighlighterIterator iterator = highlighter.createIterator(startOffset);
    while (!iterator.atEnd()) {
      int start = Math.max(iterator.getStart(), startOffset);
      int end = Math.min(iterator.getEnd(), endOffset);
      if (start >= endOffset) break;

      TextAttributes attributes = iterator.getTextAttributes();
      int fontType = attributes.getFontType();
      if ((fontType & Font.BOLD) != 0) { // suppress bold attribute
        attributes = attributes.clone();
        attributes.setFontType(fontType & ~Font.BOLD);
      }
      HighlightedRegion region = new HighlightedRegion(highlightOffsetShift + start - startOffset, highlightOffsetShift + end - startOffset, attributes);
      highlights.add(region);
      iterator.advance();
    }
  }

  public int getRowCount() {
    return myAdditionalLines.size() + 1;
  }

  @Override
  public String getTestPresentation() {
    return "Item: " + getValue().getTodoItem().getTextRange();
  }

  @Override
  public int getWeight() {
    return 5;
  }

  @Nonnull
  public List<HighlightedRegionProvider> getAdditionalLines() {
    return myAdditionalLines;
  }

  private static class AdditionalTodoLine implements HighlightedRegionProvider {
    private final String myText;
    private final List<HighlightedRegion> myHighlights;

    private AdditionalTodoLine(String text, List<HighlightedRegion> highlights) {
      myText = text;
      myHighlights = highlights;
    }

    @Override
    public Iterable<HighlightedRegion> getHighlightedRegions() {
      return myHighlights;
    }

    @Override
    public String toString() {
      return myText;
    }
  }
}
