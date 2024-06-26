/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package consulo.ide.impl.idea.ui;

import consulo.platform.Platform;
import consulo.ui.ex.awt.JBCurrentTheme;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.paint.RectanglePainter;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public interface PopupBorder extends Border {

  void setActive(boolean active);

  class Factory {
    private Factory() {
    }

    @Nonnull
    public static PopupBorder createEmpty() {
      return new BaseBorder();
    }

    @Nonnull
    public static PopupBorder create(boolean active, boolean windowWithShadow) {
      boolean visible = !(Platform.current().os().isMac() && windowWithShadow) || UIManager.getBoolean("Popup.paintBorder") == Boolean.TRUE;
      PopupBorder border = new BaseBorder(visible, JBCurrentTheme.Popup.borderColor(true), JBCurrentTheme.Popup.borderColor(false));
      border.setActive(active);
      return border;
    }

    public static PopupBorder createColored(Color color) {
      PopupBorder border = new BaseBorder(true, color, color);
      border.setActive(true);
      return border;
    }
  }

  class BaseBorder implements PopupBorder {
    private final boolean myVisible;
    private final Color myActiveColor;
    private final Color myPassiveColor;
    private boolean myActive;

    protected BaseBorder() {
      this(false, null, null);
    }

    protected BaseBorder(final boolean visible, final Color activeColor, final Color passiveColor) {
      myVisible = visible;
      myActiveColor = activeColor;
      myPassiveColor = passiveColor;
    }

    @Override
    public void setActive(final boolean active) {
      myActive = active;
    }

    @Override
    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      if (!myVisible) return;

      Color color = myActive ? myActiveColor : myPassiveColor;
      g.setColor(color);
      RectanglePainter.DRAW.paint((Graphics2D)g, x, y, width, height, null);
    }

    @Override
    public Insets getBorderInsets(final Component c) {
      return myVisible ? JBUI.insets(1) : JBUI.emptyInsets();
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  }
}
