/*
 * Copyright 2013-2016 consulo.io
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
package consulo.ui.desktop.internal;

import consulo.ui.MenuBar;
import consulo.ui.MenuItem;
import consulo.ui.desktop.internal.base.SwingComponentDelegate;

import javax.annotation.Nonnull;

import javax.swing.*;

/**
 * @author VISTALL
 * @since 14-Jun-16
 */
public class DesktopMenuBarImpl extends SwingComponentDelegate<JMenuBar> implements MenuBar {
  public DesktopMenuBarImpl() {
    myComponent = new JMenuBar();
  }

  @Override
  public void clear() {
    myComponent.removeAll();
  }

  @Nonnull
  @Override
  public MenuBar add(@Nonnull MenuItem menuItem) {
    if(menuItem instanceof JMenu) {
      myComponent.add((JMenu)menuItem);
    }
    else {
      myComponent.add((JMenu)new DesktopMenuImpl(menuItem.getText()));
    }
    return this;
  }
}
