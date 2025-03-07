// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.find.SearchSession
import com.intellij.ide.GeneralSettings
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.bindApplicationTitle
import com.intellij.ui.util.preferredHeight
import com.intellij.util.ui.JBInsets
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.exp.BlockTerminalController.BlockTerminalControllerListener
import org.jetbrains.plugins.terminal.exp.TerminalPromptController.PromptStateListener
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.*
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

class BlockTerminalView(
  private val project: Project,
  private val session: BlockTerminalSession,
  private val settings: JBTerminalSystemSettingsProviderBase,
  terminalTitle: TerminalTitle
) : TerminalContentView, TerminalCommandExecutor {
  private val controller: BlockTerminalController
  private val selectionController: TerminalSelectionController
  private val focusModel: TerminalFocusModel = TerminalFocusModel(project, this)

  val outputView: TerminalOutputView = TerminalOutputView(project, session, settings, focusModel)
  val promptView: TerminalPromptView = TerminalPromptView(project, settings, session, this)
  private var alternateBufferView: SimpleTerminalView? = null

  override val component: JComponent = BlockTerminalPanel()

  override val preferredFocusableComponent: JComponent
    get() = when {
      alternateBufferView != null -> alternateBufferView!!.preferredFocusableComponent
      controller.searchSession != null -> controller.searchSession!!.component.searchTextComponent
      promptView.component.isVisible && selectionController.primarySelection == null -> promptView.preferredFocusableComponent
      else -> outputView.preferredFocusableComponent
    }

  init {
    selectionController = TerminalSelectionController(focusModel, outputView.controller.selectionModel, outputView.controller.outputModel)
    controller = BlockTerminalController(project, session, outputView.controller, promptView.controller, selectionController, focusModel)

    Disposer.register(this, outputView)
    Disposer.register(this, promptView)

    promptView.controller.addListener(object : PromptStateListener {
      override fun promptVisibilityChanged(visible: Boolean) {
        component.revalidate()
        invokeLater {
          // do not focus the terminal if something outside the terminal is focused now
          // it can be the case when long command is finished and prompt becomes shown
          if (focusModel.isActive) {
            IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
          }
        }
      }
    })

    promptView.controller.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (promptView.component.preferredHeight != promptView.component.height) {
          component.revalidate()
        }
      }
    })

    outputView.controller.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        component.revalidate()
      }
    })

    component.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updateTerminalSize()
      }
    })

    component.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        // move focus to terminal if user clicked in the empty area
        IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
      }
    })

    session.model.addTerminalListener(object : TerminalModel.TerminalListener {
      override fun onAlternateBufferChanged(enabled: Boolean) {
        invokeLater {
          alternateBufferStateChanged(enabled)
        }
      }
    })

    terminalTitle.bindApplicationTitle(session.controller, this)

    controller.addListener(object : BlockTerminalControllerListener {
      override fun searchSessionStarted(session: SearchSession) {
        outputView.installSearchComponent(session.component)
      }

      override fun searchSessionFinished(session: SearchSession) {
        outputView.removeSearchComponent(session.component)
      }
    })

    // Forward key typed events from the output view to the prompt.
    // So, user can start typing even when some block is selected.
    // The focus and the events will just move to the prompt without an explicit focus switch.
    installTypingEventsForwarding(outputView.preferredFocusableComponent, promptView.preferredFocusableComponent)

    installPromptAndOutput()

    focusModel.addListener(object: TerminalFocusModel.TerminalFocusListener {
      override fun activeStateChanged(isActive: Boolean) {
        if (isActive) {
          if (GeneralSettings.getInstance().isSaveOnFrameDeactivation) {
            FileDocumentManager.getInstance().saveAllDocuments()
          }
        }
        else {
          // refresh when running a long-running command and switching outside the terminal
          SaveAndSyncHandler.getInstance().scheduleRefresh()
        }
      }
    })
    session.commandManager.addListener(object: ShellCommandListener {
      override fun commandFinished(event: CommandFinishedEvent) {
        SaveAndSyncHandler.getInstance().scheduleRefresh()
      }
    }, this)
  }

  override fun connectToTty(ttyConnector: TtyConnector, initialTermSize: TermSize) {
    session.controller.resize(initialTermSize, RequestOrigin.User)
    session.start(ttyConnector)
  }

  private fun alternateBufferStateChanged(enabled: Boolean) {
    if (enabled) {
      installAlternateBufferPanel()
    }
    else {
      alternateBufferView?.let { Disposer.dispose(it) }
      alternateBufferView = null
      installPromptAndOutput()
    }
    IdeFocusManager.getInstance(project).requestFocus(preferredFocusableComponent, true)
    invokeLater {
      updateTerminalSize()
    }
  }

  private fun installAlternateBufferPanel() {
    val view = SimpleTerminalView(project, settings, session, withVerticalScroll = false)
    Disposer.register(this, view)
    alternateBufferView = view

    with(component) {
      removeAll()
      add(view.component)
      revalidate()
    }
  }

  private fun installPromptAndOutput() {
    with(component) {
      removeAll()
      add(outputView.component)
      add(promptView.component)
      revalidate()
    }
  }

  private fun installTypingEventsForwarding(origin: JComponent, destination: JComponent) {
    origin.addKeyListener(object : KeyAdapter() {
      override fun keyTyped(e: KeyEvent) {
        if (e.id == KeyEvent.KEY_TYPED && destination.isShowing) {
          e.consume()
          IdeFocusManager.getInstance(project).requestFocus(destination, true)
          val newEvent = KeyEvent(destination, e.id, e.`when`, e.modifiers, e.keyCode, e.keyChar, e.keyLocation)
          destination.dispatchEvent(newEvent)
        }
      }
    })
  }

  override fun startCommandExecution(command: String) {
    controller.startCommandExecution(command)
  }

  private fun updateTerminalSize() {
    val newSize = getTerminalSize() ?: return
    controller.resize(newSize)
  }

  override fun getTerminalSize(): TermSize? {
    val (width, charSize) = if (alternateBufferView != null) {
      alternateBufferView!!.let { it.terminalWidth to it.charSize }
    }
    else outputView.let { it.terminalWidth to it.charSize }
    return if (width > 0 && component.height > 0) {
      TerminalUiUtils.calculateTerminalSize(Dimension(width, component.height), charSize)
    }
    else null
  }

  override fun isFocused(): Boolean {
    return outputView.component.hasFocus() || promptView.component.hasFocus()
  }

  override fun addTerminationCallback(onTerminated: Runnable, parentDisposable: Disposable) {
    session.addTerminationCallback(onTerminated, parentDisposable)
  }

  override fun sendCommandToExecute(shellCommand: String) {
    controller.startCommandExecution(shellCommand)
  }

  override fun dispose() {}

  private inner class BlockTerminalPanel : JPanel(), DataProvider {
    init {
      background = TerminalUi.terminalBackground
    }

    override fun getData(dataId: String): Any? {
      return when (dataId) {
        TerminalPromptController.KEY.name -> promptView.controller
        TerminalOutputController.KEY.name -> outputView.controller
        SimpleTerminalController.KEY.name -> alternateBufferView?.controller
        BlockTerminalController.KEY.name -> controller
        TerminalSelectionController.KEY.name -> selectionController
        TerminalFocusModel.KEY.name -> focusModel
        BlockTerminalSession.DATA_KEY.name -> session
        else -> null
      }
    }

    override fun doLayout() {
      val rect = bounds
      JBInsets.removeFrom(rect, insets)
      when (componentCount) {
        1 -> {
          // it is an alternate buffer editor
          val component = getComponent(0)
          component.bounds = rect
        }
        2 -> layoutPromptAndOutput(rect)
        else -> error("Maximum 2 components expected")
      }
    }

    /**
     * Place output at the top and the prompt (bottomComponent) below it.
     * Always honor the preferred height of the prompt, decrease the height of the output in favor of prompt.
     * So, initially, when output is empty, the prompt is on the top.
     * But when there is a long output, the prompt is stick to the bottom.
     */
    private fun layoutPromptAndOutput(rect: Rectangle) {
      val topComponent = getComponent(0)
      val bottomComponent = getComponent(1)
      val topPrefSize = if (topComponent.isVisible) topComponent.preferredSize else Dimension()
      val bottomPrefSize = if (bottomComponent.isVisible) bottomComponent.preferredSize else Dimension()

      val bottomHeight = max(rect.height - topPrefSize.height, bottomPrefSize.height)
      val topHeight = rect.height - bottomHeight
      topComponent.bounds = Rectangle(rect.x, rect.y, rect.width, topHeight)
      bottomComponent.bounds = Rectangle(rect.x, rect.y + topHeight, rect.width, bottomHeight)
    }
  }
}