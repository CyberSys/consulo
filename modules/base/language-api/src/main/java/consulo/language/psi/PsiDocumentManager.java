// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.language.psi;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.document.Document;
import consulo.language.file.FileViewProvider;
import consulo.project.Project;
import consulo.ui.ModalityState;
import consulo.util.concurrent.ActionCallback;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.EventListener;
import java.util.function.Supplier;

/**
 * Manages the relationship between documents and PSI trees.
 */
@ServiceAPI(value = ComponentScope.PROJECT, lazy = false)
public abstract class PsiDocumentManager {
  /**
   * Returns the document manager instance for the specified project.
   *
   * @param project the project for which the document manager is requested.
   * @return the document manager instance.
   */
  public static PsiDocumentManager getInstance(@Nonnull Project project) {
    return project.getComponent(PsiDocumentManager.class);
  }

  @Nonnull
  public static ActionCallback asyncCommitDocuments(@Nonnull Project project) {
    if (project.isDisposed()) return ActionCallback.DONE;
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (!documentManager.hasUncommitedDocuments()) {
      return ActionCallback.DONE;
    }
    final ActionCallback callback = new ActionCallback();
    documentManager.performWhenAllCommitted(callback.createSetDoneRunnable());
    return callback;
  }

  /**
   * Checks if the PSI tree for the specified document is up to date (its state reflects the latest changes made
   * to the document content).
   *
   * @param document the document to check.
   * @return true if the PSI tree for the document is up to date, false otherwise.
   */
  public abstract boolean isCommitted(@Nonnull Document document);

  /**
   * Returns the PSI file for the specified document.
   *
   * @param document the document for which the PSI file is requested.
   * @return the PSI file instance.
   */
  @Nullable
  public abstract PsiFile getPsiFile(@Nonnull Document document);

  /**
   * Returns the cached PSI file for the specified document.
   *
   * @param document the document for which the PSI file is requested.
   * @return the PSI file instance, or {@code null} if there is currently no cached PSI tree for the file.
   */
  @Nullable
  public abstract PsiFile getCachedPsiFile(@Nonnull Document document);

  /**
   * Returns the document for the specified PSI file.
   *
   * @param file the file for which the document is requested.
   * @return the document instance, or {@code null} if the file is binary or has no associated document.
   */
  @Nullable
  public abstract Document getDocument(@Nonnull PsiFile file);

  /**
   * Returns the cached document for the specified PSI file.
   *
   * @param file the file for which the document is requested.
   * @return the document instance, or {@code null} if there is currently no cached document for the file.
   */
  @Nullable
  public abstract Document getCachedDocument(@Nonnull PsiFile file);

  /**
   * Commits (updates the PSI tree for) all modified but not committed documents.
   * Before a modified document is committed, accessing its PSI may return elements
   * corresponding to original (unmodified) state of the document.<p/>
   * <p>
   * Should be called in UI thread
   */
  public abstract void commitAllDocuments();

  /**
   * Commits all modified but not committed documents under modal dialog (see {@link PsiDocumentManager#commitAllDocuments()}
   * Should be called in UI thread and outside of write-action
   *
   * @return true if the operation completed successfully, false if it was cancelled.
   */
  public abstract boolean commitAllDocumentsUnderProgress();

  /**
   * If the document is committed, runs action synchronously, otherwise schedules to execute it right after it has been committed.
   */
  public abstract void performForCommittedDocument(@Nonnull Document document, @Nonnull Runnable action);

  /**
   * Updates the PSI tree for the specified document.
   * Before a modified document is committed, accessing its PSI may return elements
   * corresponding to original (unmodified) state of the document.<p/>
   * <p>
   * Should be called in UI thread
   *
   * @param document the document to commit.
   */
  public abstract void commitDocument(@Nonnull Document document);

  /**
   * @return the document text that PSI should be based upon. For changed documents, it's their old text until the document is committed.
   * This sequence is immutable.
   * @see consulo.ide.impl.idea.util.text.ImmutableCharSequence
   */
  @Nonnull
  public abstract CharSequence getLastCommittedText(@Nonnull Document document);

  /**
   * @return for uncommitted documents, the last stamp before the document change: the same stamp that current PSI should have.
   * For committed documents, just their stamp.
   * @see Document#getModificationStamp()
   * @see FileViewProvider#getModificationStamp()
   */
  public abstract long getLastCommittedStamp(@Nonnull Document document);

  /**
   * Returns the document for specified PsiFile intended to be used when working with committed PSI, e.g. outside dispatch thread.
   *
   * @param file the file for which the document is requested.
   * @return an immutable document corresponding to the current PSI state. For committed documents, the contents and timestamp are equal to
   * the ones of {@link #getDocument(PsiFile)}. For uncommitted documents, the text is {@link #getLastCommittedText(Document)} and
   * the modification stamp is {@link #getLastCommittedStamp(Document)}.
   */
  @Nullable
  public abstract Document getLastCommittedDocument(@Nonnull PsiFile file);

  /**
   * Returns the list of documents which have been modified but not committed.
   *
   * @return the list of uncommitted documents.
   * @see #commitDocument(Document)
   */
  @Nonnull
  public abstract Document[] getUncommittedDocuments();

  /**
   * Checks if the specified document has been committed.
   *
   * @param document the document to check.
   * @return true if the document was modified but not committed, false otherwise
   * @see #commitDocument(Document)
   */
  public abstract boolean isUncommited(@Nonnull Document document);

  /**
   * Checks if any modified documents have not been committed.
   *
   * @return true if there are uncommitted documents, false otherwise
   */
  public abstract boolean hasUncommitedDocuments();

  /**
   * Commits the documents and runs the specified operation, which does not return a value, in a read action.
   * Can be called from a thread other than the Swing dispatch thread.
   *
   * @param runnable the operation to execute.
   */
  public abstract void commitAndRunReadAction(@Nonnull Runnable runnable);

  /**
   * Commits the documents and runs the specified operation, which returns a value, in a read action.
   * Can be called from a thread other than the Swing dispatch thread.
   *
   * @param computation the operation to execute.
   * @return the value returned by the operation.
   */
  public abstract <T> T commitAndRunReadAction(@Nonnull Supplier<T> computation);

  /**
   * Reparses the specified set of files after an external configuration change that would cause them to be parsed differently
   * (for example, a language level change in the settings).
   *
   * @param files            the files to reparse.
   * @param includeOpenFiles if true, the files opened in editor tabs will also be reparsed.
   */
  public abstract void reparseFiles(@Nonnull final Collection<? extends VirtualFile> files, final boolean includeOpenFiles);

  /**
   * Listener for receiving notifications about creation of {@link Document} and {@link PsiFile} instances.
   */
  public interface Listener extends EventListener {
    /**
     * Called when a document instance is created for a file.
     *
     * @param document the created document instance.
     * @param psiFile  the file for which the document was created.
     * @see PsiDocumentManager#getDocument(PsiFile)
     */
    void documentCreated(@Nonnull Document document, @Nullable PsiFile psiFile);

    /**
     * Called when a file instance is created for a document.
     *
     * @param file     the created file instance.
     * @param document the document for which the file was created.
     * @see PsiDocumentManager#getDocument(PsiFile)
     */
    default void fileCreated(@Nonnull PsiFile file, @Nonnull Document document) {
    }
  }

  /**
   * @deprecated Use message bus {@link Listener#TOPIC}.
   */
  @Deprecated
  public abstract void addListener(@Nonnull Listener listener);

  /**
   * @deprecated Use message bus {@link Listener#TOPIC}.
   */
  @Deprecated
  public abstract void removeListener(@Nonnull Listener listener);

  /**
   * Checks if the PSI tree corresponding to the specified document has been modified and the changes have not
   * yet been applied to the document. Documents in that state cannot be modified directly, because such changes
   * would conflict with the pending PSI changes. Changes made through PSI are always applied in the end of a write action,
   * and can be applied in the middle of a write action by calling {@link #doPostponedOperationsAndUnblockDocument}.
   *
   * @param doc the document to check.
   * @return true if the corresponding PSI has changes that haven't been applied to the document.
   */
  public abstract boolean isDocumentBlockedByPsi(@Nonnull Document doc);

  /**
   * Applies pending changes made through the PSI to the specified document.
   *
   * @param doc the document to apply the changes to.
   */
  public abstract void doPostponedOperationsAndUnblockDocument(@Nonnull Document doc);

  /**
   * Defer action until all documents are committed.
   * Must be called from the EDT only.
   *
   * @param action to run when all documents committed
   * @return true if action was run immediately (i.e. all documents are already committed)
   */
  public abstract boolean performWhenAllCommitted(@Nonnull Runnable action);

  /**
   * Same as {@link #performLaterWhenAllCommitted(Runnable, ModalityState)} using {@link ModalityState#defaultModalityState()}
   */
  public abstract void performLaterWhenAllCommitted(@Nonnull Runnable runnable);

  /**
   * Schedule the runnable to be executed on Swing thread when all the documents are committed at some later moment in a given modality state.
   * The runnable is guaranteed to be invoked when no write action is running, and not immediately.
   * If the project is disposed before such moment, the runnable is not run.
   */
  public abstract void performLaterWhenAllCommitted(@Nonnull Runnable runnable, ModalityState modalityState);

  /**
   * Return true if document under synchronization
   */
  public boolean isUnderSynchronization(@Nonnull Document document) {
    return false;
  }

  /**
   * Check file content and document content for equals, and drop error into log if not
   */
  public boolean checkConsistency(@Nonnull PsiFile psiFile, @Nonnull Document document) {
    return true;
  }
}
