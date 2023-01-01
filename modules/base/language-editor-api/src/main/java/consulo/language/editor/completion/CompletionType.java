package consulo.language.editor.completion;

/**
 * @author peter
 */
public enum CompletionType {
  BASIC,
  SMART,

  /**
   * Only to be passed to {@link CompletionService#getVariantsFromContributors(CompletionParameters, CompletionContributor, Consumer)}
   * to invoke special class-name providers for various file types where those class names are applicable (e.g. xml, txt, properties, custom)
   */
  CLASS_NAME
}
