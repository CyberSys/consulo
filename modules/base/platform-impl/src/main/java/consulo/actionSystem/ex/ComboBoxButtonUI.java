/*
 * Copyright 2013-2018 consulo.io
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
package consulo.actionSystem.ex;

import com.intellij.util.FieldAccessor;
import com.intellij.util.ReflectionUtil;

import javax.swing.*;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;

/**
 * @author VISTALL
 * @since 10/4/18
 */
public class ComboBoxButtonUI extends ComboBoxUI {
  private class HackComboBoxPopup implements ComboPopup {
    private ComboBoxButtonImpl myButton;

    public HackComboBoxPopup(ComboBoxButtonImpl button) {
      myButton = button;
    }

    @Override
    public void show() {
      myButton.showPopup0();
    }

    @Override
    public void hide() {
      myButton.hidePopup0();
    }

    @Override
    public boolean isVisible() {
      return myButton.isPopupVisible0();
    }

    @Override
    public JList getList() {
      return null;
    }

    @Override
    public MouseListener getMouseListener() {
      return new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          show();
        }
      };
    }

    @Override
    public MouseMotionListener getMouseMotionListener() {
      return null;
    }

    @Override
    public KeyListener getKeyListener() {
      return null;
    }

    @Override
    public void uninstallingUI() {
    }
  }

  private static Field ourPopupField = ReflectionUtil.getDeclaredField(BasicComboBoxUI.class, "popup");

  private static Field popupMouseListener = ReflectionUtil.getDeclaredField(BasicComboBoxUI.class, "popupMouseListener");
  private static Field popupMouseMotionListener = ReflectionUtil.getDeclaredField(BasicComboBoxUI.class, "popupMouseMotionListener");
  private static Field popupKeyListener = ReflectionUtil.getDeclaredField(BasicComboBoxUI.class, "popupKeyListener");
  private static FieldAccessor<BasicComboBoxUI, JButton> arrowButton = new FieldAccessor<>(BasicComboBoxUI.class, "arrowButton");

  protected BasicComboBoxUI myDelegateUI;

  private MouseListener myMouseListener;

  public ComboBoxButtonUI(ComboBoxUI ui) {
    myDelegateUI = (BasicComboBoxUI)ui;
  }

  @Override
  public void installUI(JComponent c) {
    myDelegateUI.installUI(c);

    ComboBoxButtonImpl comboBoxButton = (ComboBoxButtonImpl)c;

    try {
      // unregister native popup
      ComboPopup o = (ComboPopup)ourPopupField.get(myDelegateUI);
      if (o != null) {
        o.uninstallingUI();

        myDelegateUI.unconfigureArrowButton();

        KeyListener keyListener = (KeyListener)popupKeyListener.get(myDelegateUI);
        if (keyListener != null) {
          c.removeKeyListener(keyListener);

          popupKeyListener.set(myDelegateUI, new KeyAdapter() {
          });
        }

        MouseListener mouseListener = (MouseListener)popupMouseListener.get(myDelegateUI);
        if (mouseListener != null) {
          c.removeMouseListener(mouseListener);

          popupMouseListener.set(myDelegateUI, new MouseAdapter() {
          });
        }

        MouseMotionListener mouseMotionListener = (MouseMotionListener)popupMouseMotionListener.get(myDelegateUI);
        if (mouseMotionListener != null) {
          c.removeMouseMotionListener(mouseMotionListener);

          popupMouseMotionListener.set(myDelegateUI, new MouseMotionAdapter() {
          });
        }
      }

      ourPopupField.set(myDelegateUI, new HackComboBoxPopup(comboBoxButton));

      myMouseListener = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          comboBoxButton.showPopup0();
        }
      };

      c.addMouseListener(myMouseListener);

      myDelegateUI.configureArrowButton();
    }
    catch (IllegalAccessException e) {
      throw new Error(e);
    }
  }

  @Override
  public void uninstallUI(JComponent c) {
    myDelegateUI.uninstallUI(c);
    c.removeMouseListener(myMouseListener);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    myDelegateUI.paint(g, c);
  }

  @Override
  public void update(Graphics g, JComponent c) {
    myDelegateUI.update(g, c);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return myDelegateUI.getPreferredSize(c);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return myDelegateUI.getMinimumSize(c);
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return myDelegateUI.getMaximumSize(c);
  }

  @Override
  public void setPopupVisible(JComboBox c, boolean v) {
    ComboBoxButtonImpl button = (ComboBoxButtonImpl)c;
    if (v) {
      button.showPopup0();
    }
    else {
      button.hidePopup0();
    }
  }

  @Override
  public boolean isPopupVisible(JComboBox c) {
    ComboBoxButtonImpl button = (ComboBoxButtonImpl)c;
    return button.isPopupVisible0();
  }

  @Override
  public boolean isFocusTraversable(JComboBox c) {
    return myDelegateUI.isFocusTraversable(c);
  }
}
