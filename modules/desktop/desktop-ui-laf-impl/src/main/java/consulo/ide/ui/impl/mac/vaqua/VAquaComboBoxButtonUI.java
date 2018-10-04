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
package consulo.ide.ui.impl.mac.vaqua;

import consulo.actionSystem.ex.ComboBoxButtonUI;
import org.violetlib.aqua.AquaAppearance;
import org.violetlib.aqua.AquaComponentUI;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.plaf.ComboBoxUI;

/**
 * @author VISTALL
 * @since 10/4/18
 */
public class VAquaComboBoxButtonUI extends ComboBoxButtonUI implements AquaComponentUI {
  public VAquaComboBoxButtonUI(ComboBoxUI ui) {
    super(ui);
  }

  @Override
  public void appearanceChanged(@Nonnull JComponent jComponent, @Nonnull AquaAppearance aquaAppearance) {
    if(myDelegateUI instanceof AquaComponentUI) {
      ((AquaComponentUI)myDelegateUI).appearanceChanged(jComponent, aquaAppearance);
    }
  }

  @Override
  public void activeStateChanged(@Nonnull JComponent jComponent, boolean b) {
    if(myDelegateUI instanceof AquaComponentUI) {
      ((AquaComponentUI)myDelegateUI).activeStateChanged(jComponent, b);
    }
  }
}
