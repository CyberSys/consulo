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
package consulo.ui.layout;

import consulo.ui.*;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.internal.UIConstant;
import consulo.ui.internal.UIInternal;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 12-Jun-16
 */
public interface HorizontalLayout extends Layout {
  @Nonnull
  static HorizontalLayout create() {
    return create(UIConstant.DEFAULT_SPACING_PX);
  }

  @Nonnull
  static HorizontalLayout create(int gapInPixels) {
    return UIInternal.get()._Layouts_horizontal(gapInPixels);
  }

  @Nonnull
  @RequiredUIAccess
  default HorizontalLayout add(@Nonnull PseudoComponent component) {
    return add(component.getComponent());
  }

  @Nonnull
  @RequiredUIAccess
  HorizontalLayout add(@Nonnull Component component);
}
