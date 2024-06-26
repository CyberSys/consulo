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
package consulo.ui.ex.awt;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.function.Consumer;

public abstract class LinkMouseListenerBase<T> extends ClickListener implements MouseMotionListener {
  public static void installSingleTagOn(@Nonnull SimpleColoredComponent component) {
    new LinkMouseListenerBase<Consumer<MouseEvent>>() {
      @Nullable
      @Override
      protected Consumer<MouseEvent> getTagAt(@Nonnull MouseEvent e) {
        //noinspection unchecked
        return (Consumer<MouseEvent>)((SimpleColoredComponent)e.getSource()).getFragmentTagAt(e.getX());
      }

      @Override
      protected void handleTagClick(@Nullable Consumer<MouseEvent> tag, @Nonnull MouseEvent event) {
        if (tag != null) {
          tag.accept(event);
        }
      }
    }.installOn(component);
  }

  @Nullable
  protected abstract T getTagAt(@Nonnull MouseEvent e);

  @Override
  public boolean onClick(@Nonnull MouseEvent e, int clickCount) {
    if (e.getButton() == MouseEvent.BUTTON1) {
      handleTagClick(getTagAt(e), e);
    }
    return false;
  }

  protected void handleTagClick(@Nullable T tag, @Nonnull MouseEvent event) {
    if (tag instanceof Runnable) {
      ((Runnable)tag).run();
    }
  }

  @Override
  public void mouseDragged(MouseEvent e) {
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    Component component = (Component)e.getSource();
    Object tag = getTagAt(e);
    if (tag != null) {
      component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    else {
      component.setCursor(Cursor.getDefaultCursor());
    }
  }

  @Override
  public void installOn(@Nonnull Component component) {
    super.installOn(component);

    component.addMouseMotionListener(this);
  }
}
