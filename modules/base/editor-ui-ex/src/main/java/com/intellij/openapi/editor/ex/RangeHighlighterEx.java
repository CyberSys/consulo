// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import javax.annotation.Nonnull;

import java.util.Comparator;

public interface RangeHighlighterEx extends RangeHighlighter, RangeMarkerEx {
  RangeHighlighterEx[] EMPTY_ARRAY = new RangeHighlighterEx[0];

  boolean isAfterEndOfLine();

  void setAfterEndOfLine(boolean value);

  int getAffectedAreaStartOffset();

  int getAffectedAreaEndOffset();

  void setTextAttributes(@Nonnull TextAttributes textAttributes);

  /**
   * @see #isVisibleIfFolded()
   */
  void setVisibleIfFolded(boolean value);

  /**
   * If {@code true}, there will be a visual indication that this highlighter is present inside a collapsed fold region.
   * By default it won't happen, use {@link #setVisibleIfFolded(boolean)} to change it.
   *
   * @see FoldRegion#setInnerHighlightersMuted(boolean)
   */
  boolean isVisibleIfFolded();

  /**
   * If {@code true}, this highlighter is persistent and is retained between code analyzer runs and IDE restarts.
   *
   * @see MarkupModelEx#addPersistentLineHighlighter(TextAttributesKey, int, int)
   * @see MarkupModelEx#addRangeHighlighterAndChangeAttributes(TextAttributesKey, int, int, int, HighlighterTargetArea, boolean, Consumer)
   */
  default boolean isPersistent() {
    return false;
  }

  
  default boolean isRenderedInGutter() {
    return getGutterIconRenderer() != null || getLineMarkerRenderer() != null;
  }

  /**
   * @deprecated Use {@link #getErrorStripeMarkColor(EditorColorsScheme)} directly,
   * it's impossible to tell if a highlighter should be rendered in a scroll bar since an editor can have a custom color scheme
   */
  @Deprecated
  default boolean isRenderedInScrollBar() {
    return getErrorStripeMarkColor(null) != null;
  }

  Comparator<RangeHighlighterEx> BY_AFFECTED_START_OFFSET = Comparator.comparingInt(RangeHighlighterEx::getAffectedAreaStartOffset);
}
