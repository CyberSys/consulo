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

package consulo.language.editor.impl.internal.template;

import consulo.language.ast.IElementType;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.Variable;
import consulo.language.editor.template.macro.MacroParser;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class TemplateImplUtil {
  private TemplateImplUtil() {
  }

  public static void parseVariables(CharSequence text, ArrayList<Variable> variables, @Nullable Set<String> predefinedVars) {
    TemplateTextLexer lexer = new TemplateTextLexer();
    lexer.start(text);

    while(true){
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      int start = lexer.getTokenStart();
      int end = lexer.getTokenEnd();
      String token = text.subSequence(start, end).toString();
      if (tokenType == TemplateTokenType.VARIABLE){
        String name = token.substring(1, token.length() - 1);
        boolean isFound = false;

        if (predefinedVars!=null && predefinedVars.contains(name) && !name.equals(TemplateImpl.SELECTION)){
          isFound = true;
        }
        else{
          for (Variable variable : variables) {
            if (variable.getName().equals(name)) {
              isFound = true;
              break;
            }
          }
        }

        if (!isFound){
          variables.add(new Variable(name, "", "", true));
        }
      }
      lexer.advance();
    }
  }

  public static Expression parseTemplate(@NonNls String text) {
    return MacroParser.parse(text);
  }
}
