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
package consulo.ide.impl.idea.find.impl.livePreview;

import consulo.util.lang.StringUtil;
import consulo.ui.ex.Gray;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.JBLabel;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

public class ReplacementView extends JPanel {
  private static final String MALFORMED_REPLACEMENT_STRING = "Malformed replacement string";

  @Override
  protected void paintComponent(@Nonnull Graphics graphics) {
  }

  public ReplacementView(@Nullable String replacement) {
    String textToShow = StringUtil.notNullize(replacement, MALFORMED_REPLACEMENT_STRING);
    textToShow = StringUtil.escapeXmlEntities(StringUtil.shortenTextWithEllipsis(textToShow, 500, 0, true)).replaceAll("\n+", "\n").replace("\n", "<br>");
    //noinspection HardCodedStringLiteral
    JLabel jLabel = new JBLabel("<html>" + textToShow).setAllowAutoWrapping(true);
    jLabel.setForeground(replacement != null ? new JBColor(Gray._240, Gray._200) : JBColor.RED);
    add(jLabel);
  }
}
