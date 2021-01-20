// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.jetbrains.annotations.Contract;
import javax.annotation.Nonnull;

public abstract class ImmutableCharSequence implements CharSequence {

  @Contract(pure = true)
  public static CharSequence asImmutable(@Nonnull final CharSequence cs) {
    return isImmutable(cs) ? cs : cs.toString();
  }

  private static boolean isImmutable(@Nonnull final CharSequence cs) {
    return cs instanceof ImmutableCharSequence || cs instanceof CharSequenceSubSequence && isImmutable(((CharSequenceSubSequence)cs).getBaseSequence());
  }

  @Contract(pure = true)
  public abstract ImmutableCharSequence concat(@Nonnull CharSequence sequence);

  @Contract(pure = true)
  public abstract ImmutableCharSequence insert(int index, @Nonnull CharSequence seq);

  @Contract(pure = true)
  public abstract ImmutableCharSequence delete(int start, int end);

  @Contract(pure = true)
  public abstract ImmutableCharSequence subtext(int start, int end);

  @Contract(pure = true)
  public ImmutableCharSequence replace(int start, int end, @Nonnull CharSequence seq) {
    return delete(start, end).insert(start, seq);
  }

  @Nonnull
  @Override
  public abstract String toString();
}
