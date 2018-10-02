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
package consulo.ide.ui.impl.mac;

import org.violetlib.vappearances.VAppearance;
import org.violetlib.vappearances.VAppearances;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * @author VISTALL
 * @since 10/1/18
 */
public class MacVAquaUIDecorator extends MacAquaUIDecorator {
  @Override
  public void init() {
    try {
      UIDefaults defaults = UIManager.getDefaults();

      VAppearance appearance = VAppearances.getApplicationEffectiveAppearance();

      setIfNotNull(appearance, defaults, "controlBackground", "Panel.background");
      setIfNotNull(appearance, defaults, "textBackground", "List.background");
      setIfNotNull(appearance, defaults, "controlBackground", "Tree.background");

      setIfNotNull(appearance, defaults, "selectedControl", "Hyperlink.linkColor");
    }
    catch (IOException e) {
    }
  }

  private static void setIfNotNull(VAppearance appearance, UIDefaults uiDefaults, String source, String target) {
    Color color = appearance.getColors().get(source);
    if(color != null) {
      uiDefaults.put(target, color);
    }
  }

  @Override
  public boolean isAvaliable() {
    LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
    return "org.violetlib.aqua.AquaLookAndFeel".equals(lookAndFeel.getClass().getName());
  }

  @Nullable
  @Override
  public Color getSidebarColor() {
    try {
      VAppearance appearance = VAppearances.getApplicationEffectiveAppearance();

      return appearance.getColors().get("selectedControl");
    }
    catch (IOException e) {
      return null;
    }
  }

  @Override
  public boolean isDark() {
    try {
      VAppearance appearance = VAppearances.getApplicationEffectiveAppearance();
      return appearance.isDark();
    }
    catch (IOException e) {
      return false;
    }
  }
}
