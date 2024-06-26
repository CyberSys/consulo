/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package consulo.ui.ex;

import consulo.colorScheme.TextAttributesKey;
import consulo.navigation.ItemPresentation;
import consulo.ui.image.Image;

import jakarta.annotation.Nullable;

/**
 * @author yole
 */
public class DelegatingItemPresentation implements ColoredItemPresentation {
  private final ItemPresentation myBase;
  private String myPresentableText;
  private String myLocationString;
  private Image myIcon;
  private boolean myCustomLocationString;
  
  public DelegatingItemPresentation(ItemPresentation base) {
    myBase = base;
  }

  public DelegatingItemPresentation withPresentableText(String presentableText) {
    myPresentableText = presentableText;
    return this;
  }

  public DelegatingItemPresentation withLocationString(@Nullable String locationString) {
    myCustomLocationString = true;
    myLocationString = locationString;
    return this;
  }

  public DelegatingItemPresentation withIcon(Image icon) {
    myIcon = icon;
    return this;
  }

  @Override
  public String getPresentableText() {
    if (myPresentableText != null) {
      return myPresentableText;
    }
    return myBase.getPresentableText();
  }

  @Override
  public String getLocationString() {
    if (myCustomLocationString) {
      return myLocationString;
    }
    return myBase.getLocationString();
  }

  @Override
  public Image getIcon() {
    if (myIcon != null) {
      return myIcon;
    }
    return myBase.getIcon();
  }

  @Override
  public TextAttributesKey getTextAttributesKey() {
    return myBase instanceof ColoredItemPresentation ? ((ColoredItemPresentation) myBase).getTextAttributesKey() : null;
  }
}
