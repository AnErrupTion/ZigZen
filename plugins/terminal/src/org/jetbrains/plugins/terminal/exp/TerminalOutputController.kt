// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jediterm.terminal.TextStyle
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.IS_OUTPUT_EDITOR_KEY
import org.jetbrains.plugins.terminal.exp.TerminalUiUtils.toTextAttributes
import org.jetbrains.plugins.terminal.exp.hyperlinks.TerminalHyperlinkHighlighter
import java.awt.Font

class TerminalOutputController(
  project: Project,
  private val editor: EditorEx,
  private val session: BlockTerminalSession,
  private val settings: JBTerminalSystemSettingsProviderBase,
  focusModel: TerminalFocusModel
) : TerminalModel.TerminalListener {
  val outputModel: TerminalOutputModel = TerminalOutputModel(editor)
  val selectionModel: TerminalSelectionModel = TerminalSelectionModel(outputModel)
  private val scraper: ShellCommandOutputScraper = ShellCommandOutputScraper(session)
  private val blocksDecorator: TerminalBlocksDecorator = TerminalBlocksDecorator(session.colorPalette, outputModel, focusModel, selectionModel, editor)
  private val textHighlighter: TerminalTextHighlighter = TerminalTextHighlighter(outputModel)

  private var caretPainter: TerminalCaretPainter? = null

  @Volatile
  private var keyEventsListenerDisposable: Disposable? = null

  @Volatile
  private var mouseAndContentListenersDisposable: Disposable? = null
  private val hyperlinkHighlighter: TerminalHyperlinkHighlighter = TerminalHyperlinkHighlighter(project, outputModel, session)

  init {
    editor.putUserData(IS_OUTPUT_EDITOR_KEY, true)
    editor.highlighter = textHighlighter
    session.model.addTerminalListener(this)

    session.addCommandListener(object : ShellCommandListener {
      override fun clearInvoked() {
        val disposable = Disposer.newDisposable()
        // clear all blocks when command is finished and then remove listener
        session.addCommandListener(object : ShellCommandListener {
          override fun commandFinished(event: CommandFinishedEvent) {
            invokeLater {
              outputModel.clearBlocks()
            }
            Disposer.dispose(disposable)
          }
        }, disposable)
      }
    })
  }

  @RequiresEdt
  fun startCommandBlock(command: String?, prompt: PromptRenderingInfo?) {
    val block = outputModel.createBlock(command, prompt)
    if (block.withPrompt) {
      val highlightings = adjustHighlightings(block.prompt!!.highlightings, block.startOffset)
      appendLineToBlock(block, block.prompt.text, highlightings, block.withCommand)
    }
    if (block.withCommand) {
      appendLineToBlock(block, command!!, listOf(createCommandHighlighting(block)), false)
    }
    if (block.withPrompt || block.withCommand) {
      blocksDecorator.installDecoration(block, isFirstBlock = outputModel.getBlocksSize() == 1)
    }

    scrollToBottom()
    installRunningCommandListeners()
  }

  private fun installRunningCommandListeners() {
    val mouseAndContentDisposable = Disposer.newDisposable().also { Disposer.register(session, it) }
    mouseAndContentListenersDisposable = mouseAndContentDisposable
    val keyEventsDisposable = Disposer.newDisposable().also { Disposer.register(session, it) }
    keyEventsListenerDisposable = keyEventsDisposable

    val eventsHandler = BlockTerminalEventsHandler(session, settings, this)
    setupKeyEventDispatcher(editor, settings, eventsHandler, outputModel, selectionModel, keyEventsDisposable)
    setupMouseListener(editor, settings, session.model, eventsHandler, mouseAndContentDisposable)
    setupContentListener(mouseAndContentDisposable)

    val caretModel = TerminalCaretModel(session, outputModel, editor, mouseAndContentDisposable)
    caretPainter = TerminalCaretPainter(caretModel, outputModel, selectionModel, editor)
    Disposer.register(keyEventsDisposable, caretPainter!!)
  }

  private fun disposeRunningCommandListeners() {
    mouseAndContentListenersDisposable?.let { Disposer.dispose(it) }
    mouseAndContentListenersDisposable = null
    runInEdt {
      // Dispose at EDT, because there can be a race when focus listener trying to install TerminalEventDispatcher on EDT,
      // and this disposable is disposed on BGT. The dispatcher won't be removed as a result.
      keyEventsListenerDisposable?.let { Disposer.dispose(it) }
      keyEventsListenerDisposable = null
      caretPainter = null
    }
  }

  fun finishCommandBlock(exitCode: Int) {
    disposeRunningCommandListeners()
    updateEditorContent(scraper.scrapeOutput())
    invokeLater {
      // the last block can be null in case of 'clear' command
      val block = outputModel.getLastBlock() ?: return@invokeLater
      val document = editor.document
      val lastLineInd = document.getLineNumber(block.endOffset)
      val lastLineStart = document.getLineStartOffset(lastLineInd)
      val lastLineText = document.getText(TextRange(lastLineStart, block.endOffset))
      // remove the line with empty prompt
      if (lastLineText.isBlank()) {
        // remove also the line break if it is not the first block
        val startRemoveOffset = lastLineStart - if (lastLineStart > 0) 1 else 0
        outputModel.deleteDocumentRange(block, TextRange(startRemoveOffset, block.endOffset))
      }
      if (document.getText(block.textRange).isBlank()) {
        outputModel.removeBlock(block)
      }
      else {
        outputModel.setBlockInfo(block, CommandBlockInfo(exitCode))
      }
    }
  }

  @RequiresEdt
  fun insertEmptyLine() {
    outputModel.closeLastBlock()
    editor.document.insertString(editor.document.textLength, "\n")
    scrollToBottom()
  }

  @RequiresEdt
  fun scrollToBottom() {
    val scrollingModel = editor.scrollingModel
    // disable animation to perform scrolling atomically
    scrollingModel.disableAnimation()
    try {
      val visibleArea = editor.scrollingModel.visibleArea
      scrollingModel.scrollVertically(editor.contentComponent.height - visibleArea.height)
    }
    finally {
      scrollingModel.enableAnimation()
    }
  }

  override fun onAlternateBufferChanged(enabled: Boolean) {
    if (enabled) {
      // stop updating the block content, because alternate buffer application will be shown in a separate component
      disposeRunningCommandListeners()
    }
    else {
      installRunningCommandListeners()
    }
  }

  private fun setupContentListener(disposable: Disposable) {
    scraper.addListener(object : ShellCommandOutputListener {
      override fun commandOutputChanged(output: StyledCommandOutput) {
        updateEditorContent(output)
      }
    }, disposable)
  }

  private fun updateEditorContent(output: StyledCommandOutput) {
    // Can not use invokeAndWait here because deadlock may happen. TerminalTextBuffer is locked at this place,
    // and EDT can be frozen now trying to acquire this lock
    invokeLater(ModalityState.any()) {
      if (!editor.isDisposed) {
        // there can be no last block for 'clear' command, because it removes all blocks
        val baseOffset = outputModel.getLastBlock()?.outputStartOffset ?: return@invokeLater
        updateEditor(toHighlightedCommandOutput(output, baseOffset))
      }
    }
  }

  private fun toHighlightedCommandOutput(output: StyledCommandOutput, baseOffset: Int): CommandOutput {
    return CommandOutput(output.text, output.styleRanges.map {
      HighlightingInfo(baseOffset + it.startOffset, baseOffset + it.endOffset, it.style.toTextAttributes())
    })
  }

  private fun updateEditor(output: CommandOutput) {
    val block = outputModel.getLastBlock() ?: error("No active block")
    val wasAtBottom = editor.scrollingModel.visibleArea.let { it.y + it.height } == editor.contentComponent.height

    // highlightings are collected only for output, so add prompt and command highlightings to the first place
    val highlightings = if (block.withPrompt || block.withCommand) {
      output.highlightings.toMutableList().also { highlightings ->
        if (block.withCommand) {
          highlightings.add(0, createCommandHighlighting(block))
        }
        if (block.withPrompt) {
          highlightings.addAll(0, adjustHighlightings(block.prompt!!.highlightings, block.startOffset))
        }
      }
    }
    else output.highlightings

    outputModel.putHighlightings(block, highlightings)
    // add leading \n here, because \n is not added after command in `startCommandBlock`
    val prefix = "\n".takeIf { block.withPrompt || block.withCommand }.orEmpty()
    editor.document.replaceString(block.outputStartOffset - prefix.length, block.endOffset, prefix + output.text)
    outputModel.trimOutput()
    hyperlinkHighlighter.highlightHyperlinks(block)

    // Install decorations lazily, only if there is some text.
    // ZSH prints '%' character on startup and then removing it immediately, so ignore this character to avoid blinking.
    // This hack can be solved by debouncing the update text requests.
    if (output.text.isNotBlank() && output.text.trim() != "%") {
      blocksDecorator.installDecoration(block, isFirstBlock = outputModel.getBlocksSize() == 1)
    }

    // scroll to bottom only if we were at the bottom before applying the changes
    // so the user is free to look at the other commands output while active command is running
    if (wasAtBottom) {
      scrollToBottom()
    }
    // caret highlighter can be removed at this moment, because we replaced the text of the block
    // so, call repaint manually
    caretPainter?.repaint()
  }

  private fun TextStyle.toTextAttributes(): TextAttributes = this.toTextAttributes(session.colorPalette)

  private fun appendLineToBlock(block: CommandBlock, text: String, highlightings: List<HighlightingInfo>, addTrailingNewLine: Boolean) {
    val existingHighlightings = outputModel.getHighlightings(block) ?: emptyList()
    outputModel.putHighlightings(block, existingHighlightings + highlightings)
    editor.document.insertString(block.endOffset, if (addTrailingNewLine) text + "\n" else text)
  }

  /** It is implied that [CommandBlock.command] is not null */
  private fun createCommandHighlighting(block: CommandBlock): HighlightingInfo {
    val attributes = TextAttributes(TerminalUi.commandForeground, null, null, null, Font.BOLD)
    return HighlightingInfo(block.commandStartOffset, block.commandStartOffset + block.command!!.length, attributes)
  }

  private fun adjustHighlightings(highlightings: List<HighlightingInfo>, baseOffset: Int): List<HighlightingInfo> {
    return highlightings.map {
      HighlightingInfo(baseOffset + it.startOffset, baseOffset + it.endOffset, it.textAttributes)
    }
  }

  fun addDocumentListener(listener: DocumentListener, disposable: Disposable? = null) {
    if (disposable != null) {
      editor.document.addDocumentListener(listener, disposable)
    }
    else editor.document.addDocumentListener(listener)
  }

  private data class CommandOutput(val text: String, val highlightings: List<HighlightingInfo>)

  companion object {
    val KEY: DataKey<TerminalOutputController> = DataKey.create("TerminalOutputController")
  }
}