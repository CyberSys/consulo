/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.ui.ex.awt.internal;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.ui.Window;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author pegov
 */
@ServiceAPI(ComponentScope.APPLICATION)
public abstract class MacMessages {
  @Messages.YesNoCancelResult
  public abstract int showYesNoCancelDialog(@Nonnull String title,
                                            String message,
                                            @Nonnull String defaultButton,
                                            String alternateButton,
                                            String otherButton,
                                            @Nullable Window window,
                                            @Nullable DialogWrapper.DoNotAskOption doNotAskOption);

  public static MacMessages getInstance() {
    return  Application.get().getInstance(MacMessages.class);
  }

  /**
   * Buttons are placed starting near the right side of the alert and going toward the left side
   * (for languages that read left to right). The first three buttons are identified positionally as
   * NSAlertFirstButtonReturn, NSAlertSecondButtonReturn, NSAlertThirdButtonReturn in the return-code parameter evaluated by the modal
   * delegate. Subsequent buttons are identified as NSAlertThirdButtonReturn +n, where n is an integer
   * <p>
   * By default, the first button has a key equivalent of Return,
   * any button with a title of "Cancel" has a key equivalent of Escape,
   * and any button with the title "Don't Save" has a key equivalent of Command-D (but only if it is not the first button).
   * <p>
   * http://developer.apple.com/library/mac/#documentation/Cocoa/Reference/ApplicationKit/Classes/NSAlert_Class/Reference/Reference.html
   * <p>
   * Please, note that Cancel is supposed to be the last button!
   *
   * @return number of button pressed: from 0 up to buttons.length-1 inclusive, or -1 for Cancel
   */
  public abstract int showMessageDialog(@Nonnull String title,
                                        String message,
                                        @Nonnull String[] buttons,
                                        boolean errorStyle,
                                        @Nullable Window window,
                                        int defaultOptionIndex,
                                        int focusedOptionIndex,
                                        @Nullable DialogWrapper.DoNotAskOption doNotAskDialogOption);

  public abstract void showOkMessageDialog(@Nonnull String title, String message, @Nonnull String okText, @Nullable Window window);

  public abstract void showOkMessageDialog(@Nonnull String title, String message, @Nonnull String okText);

  /**
   * @return {@link Messages#YES} if user pressed "Yes" or {@link Messages#NO} if user pressed "No" button.
   */
  @Messages.YesNoResult
  public abstract int showYesNoDialog(@Nonnull String title, String message, @Nonnull String yesButton, @Nonnull String noButton, @Nullable Window window);

  /**
   * @return {@link Messages#YES} if user pressed "Yes" or {@link Messages#NO} if user pressed "No" button.
   */
  @Messages.YesNoResult
  public abstract int showYesNoDialog(@Nonnull String title,
                                      String message,
                                      @Nonnull String yesButton,
                                      @Nonnull String noButton,
                                      @Nullable Window window,
                                      @Nullable DialogWrapper.DoNotAskOption doNotAskDialogOption);

  public abstract void showErrorDialog(@Nonnull String title, String message, @Nonnull String okButton, @Nullable Window window);
}