/*
 * Copyright 2013-2023 consulo.io
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

import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.ui.layout.event.LoadingWrappedLayoutListener;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 29/01/2023
 */
public interface LoadingWrappedLayout extends Layout {
  void startLoading();

  void stopLoading();

  boolean isLoading();

  /**
   * @param loadingText if null will set default value
   */
  void setLoadingText(@Nullable LocalizeValue loadingText);

  @Nonnull
  Disposable addLoadingListener(@Nonnull LoadingWrappedLayoutListener listener);
}
