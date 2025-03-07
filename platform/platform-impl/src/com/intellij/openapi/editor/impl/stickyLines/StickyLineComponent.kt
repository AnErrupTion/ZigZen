// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUIUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.util.ui.MouseEventAdapter
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities

/**
 * Represents one editor's line (gutter + line text)
 */
internal class StickyLineComponent(private val editor: EditorEx) : JComponent() {
  var primaryVisualLine: Int = -1
  var scopeVisualLine: Int = -1
  private var offsetOnClick: Int = -1
  private var debugText: String? = null
  private var dumbTextImage: BufferedImage? = null
  private var isHovered: Boolean = false
  private val mouseListener = StickyMouseListener()

  init {
    border = null
    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)
    addMouseWheelListener(mouseListener)
  }

  fun setLine(
    primaryVisualLine: VisualPosition,
    scopeVisualLine: VisualPosition,
    offsetOnClick: Int,
    debugText: String?,
  ) {
    setLine(primaryVisualLine.line, scopeVisualLine.line, offsetOnClick, debugText)
  }

  fun resetLine() {
    setLine(-1, -1, -1, null)
  }

  private fun setLine(
    primaryVisualLine: Int,
    scopeVisualLine: Int,
    offsetOnClick: Int,
    debugText: String?,
  ) {
    this.primaryVisualLine = primaryVisualLine
    this.scopeVisualLine = scopeVisualLine
    this.offsetOnClick = offsetOnClick
    this.debugText = debugText
    this.dumbTextImage = null
    this.isHovered = false
    this.mouseListener.isPopup = false
    this.mouseListener.isGutterHovered = false
  }

  fun isEmpty(): Boolean {
    return primaryVisualLine == -1 || scopeVisualLine == -1 || offsetOnClick == -1
  }

  fun startDumb() {
    // special mode when the line is rendered as an image to avoid flicking,
    // the mode ends when the corresponding editor's mode ends
    paintStickyLine(graphicsOrDumb = null)
  }

  override fun paintComponent(g: Graphics) {
    paintStickyLine(g)
  }

  private fun paintStickyLine(graphicsOrDumb: Graphics?) {
    assert(!isEmpty()) { "sticky panel should mark this line as not visible" }
    val stickyLineY = editor.visualLineToY(primaryVisualLine)
    val lineHeight = lineHeight()
    val gutterWidth = editor.gutterComponentEx.width
    val textWidth = lineWidth() - gutterWidth
    (editor as EditorImpl).isStickyLinePainting = true
    try {
      editor.isStickyLineHovered = isHovered
      if (graphicsOrDumb != null) {
        paintGutter(graphicsOrDumb, stickyLineY, lineHeight, gutterWidth)
        paintText(graphicsOrDumb, stickyLineY, lineHeight, gutterWidth, textWidth)
      } else {
        dumbTextImage = prepareDumbTextImage(stickyLineY, lineHeight, textWidth)
      }
    } finally {
      editor.isStickyLinePainting = false
    }
  }

  private fun paintGutter(g: Graphics, stickyLineY: Int, lineHeight: Int, gutterWidth: Int) {
    g.translate(0, -stickyLineY)
    g.setClip(0, stickyLineY, gutterWidth, lineHeight)
    editor.gutterComponentEx.paint(g)
  }

  private fun paintText(g: Graphics, stickyLineY: Int, lineHeight: Int, gutterWidth: Int, textWidth: Int) {
    g.translate(gutterWidth, 0)
    g.setClip(0, stickyLineY, textWidth, lineHeight)
    val textImage = dumbTextImage
    if (textImage != null && (editor as EditorImpl).isDumb) {
      StartupUiUtil.drawImage(g, textImage, 0, stickyLineY, null)
    } else {
      doPaintText(g)
      dumbTextImage = null
    }
  }

  private fun prepareDumbTextImage(stickyLineY: Int, lineHeight: Int, textWidth: Int): BufferedImage {
    val textImage = UIUtil.createImage(
      editor.contentComponent,
      textWidth,
      lineHeight,
      BufferedImage.TYPE_INT_RGB,
    )
    val textGraphics = textImage.graphics
    EditorUIUtil.setupAntialiasing(textGraphics)
    textGraphics.translate(0, -stickyLineY)
    textGraphics.setClip(0, stickyLineY, textWidth, lineHeight)
    doPaintText(textGraphics)
    textGraphics.dispose()
    return textImage
  }

  private fun doPaintText(g: Graphics) {
    editor.contentComponent.paint(g)
  }

  private fun lineWidth(): Int {
    return (editor as EditorImpl).stickyLinesPanelWidth
  }

  private fun lineHeight(): Int {
    return editor.lineHeight
  }

  override fun toString(): String {
    return "${debugText ?: ""}(primary=$primaryVisualLine, scope=$scopeVisualLine, onClick=$offsetOnClick)"
  }

  internal class MyMouseEvent(e: MouseEvent, source: Component, y: Int) : MouseEvent(
    source,
    e.id,
    e.`when`,
    UIUtil.getAllModifiers(e),
    e.x,
    y,
    e.clickCount,
    e.isPopupTrigger,
    e.button,
  )

  private inner class StickyMouseListener : MouseListener, MouseMotionListener, MouseWheelListener {
    private val popMenu: JPopupMenu
    var isPopup = false
    var isGutterHovered = false

    init {
      val actionManager = ActionManager.getInstance()
      val actionGroup = actionManager.getAction("EditorStickyLinesSettings") as DefaultActionGroup
      popMenu = actionManager.createActionPopupMenu("StickyLine", actionGroup).component
    }

    override fun mousePressed(e: MouseEvent?)         = handleEvent(e)
    override fun mouseReleased(e: MouseEvent?)        = handleEvent(e)
    override fun mouseClicked(e: MouseEvent?)         = handleEvent(e)
    override fun mouseEntered(e: MouseEvent?)         = handleEvent(e)
    override fun mouseExited(e: MouseEvent?)          = handleEvent(e)
    override fun mouseDragged(e: MouseEvent?)         = handleEvent(e)
    override fun mouseMoved(e: MouseEvent?)           = handleEvent(e)
    override fun mouseWheelMoved(e: MouseWheelEvent?) = handleEvent(e)

    private fun handleEvent(event: MouseEvent?) {
      if (event == null || event.isConsumed || isEmpty()) {
        return
      }
      when (event.id) {
        MouseEvent.MOUSE_ENTERED,
        MouseEvent.MOUSE_EXITED,
        MouseEvent.MOUSE_MOVED -> {
          onHover(event)
        }
        MouseEvent.MOUSE_PRESSED,
        MouseEvent.MOUSE_RELEASED,
        MouseEvent.MOUSE_CLICKED -> {
          if (isGutterEvent(event)) {
            gutterClick(event)
          } else {
            popupOrNavigate(event)
          }
        }
        MouseEvent.MOUSE_WHEEL -> {
          forwardToScrollPane(event)
        }
      }
      event.consume()
    }

    private fun forwardToScrollPane(event: MouseEvent) {
      val converted = MouseEventAdapter.convert(event, editor.scrollPane)
      editor.scrollPane.dispatchEvent(converted)
    }

    private fun onHover(event: MouseEvent) {
      val isGutterEvent = isGutterEvent(event)
      when (event.id) {
        MouseEvent.MOUSE_ENTERED -> {
          onTextHover(!isGutterHovered)
          onGutterHover(isGutterHovered)
        }
        MouseEvent.MOUSE_EXITED -> {
          onTextHover(false)
          onGutterHover(false)
        }
        MouseEvent.MOUSE_MOVED -> {
          if (isGutterEvent && isHovered && !isGutterHovered) {
            onTextHover(false)
            onGutterHover(true)
          } else if (!isGutterEvent && !isHovered && isGutterHovered) {
            onTextHover(true)
            onGutterHover(false)
          }
        }
        else -> throwUnhandledEvent(event)
      }
    }

    private fun onTextHover(hovered: Boolean) {
      if (hovered != isHovered) {
        isHovered = hovered
        repaint()
      }
    }

    private fun onGutterHover(hovered: Boolean) {
      if (hovered != isGutterHovered) {
        isGutterHovered = hovered
        //(editor as EditorImpl).onGutterHover(hovered)
        repaint()
      }
    }

    private fun gutterClick(event: MouseEvent) {
      if (event.id == MouseEvent.MOUSE_PRESSED || (event.id == MouseEvent.MOUSE_RELEASED && event.isPopupTrigger)) {
        val converted = convert(event)
        val mouseListener = (editor as EditorImpl).mouseListener
        if (!event.isPopupTrigger) {
          event.consume()
          return
        }
        if (event.id == MouseEvent.MOUSE_PRESSED) {
          mouseListener.mousePressed(converted)
        } else {
          mouseListener.mouseReleased(converted)
        }
      }
    }

    private fun popupOrNavigate(event: MouseEvent) {
      when (event.id) {
        MouseEvent.MOUSE_PRESSED -> {
          isPopup = event.isPopupTrigger
          if (isPopup) {
            popMenu.show(event.component, event.x, event.y)
          }
        }
        MouseEvent.MOUSE_RELEASED -> {
          // From review: on some platform RELEASED event can be a popup trigger
          if (!isPopup) {
            isPopup = event.isPopupTrigger
            if (isPopup) {
              popMenu.show(event.component, event.x, event.y)
            }
          }
        }
        MouseEvent.MOUSE_CLICKED -> {
          if (!isPopup) {
            // wrap into command to support "Back navigation" IJPL-591
            CommandProcessor.getInstance().executeCommand(
              editor.project,
              Runnable {
                editor.caretModel.moveToOffset(offsetOnClick)
                editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
                editor.selectionModel.removeSelection(/* allCarets = */ true)
                IdeDocumentHistory.getInstance(editor.project).includeCurrentCommandAsNavigation()
              },
              "",
              DocCommandGroupId.noneGroupId(editor.document),
              UndoConfirmationPolicy.DEFAULT,
              editor.document
            )
          }
        }
        else -> throwUnhandledEvent(event)
      }
    }

    private fun isGutterEvent(event: MouseEvent): Boolean {
      return event.x <= editor.gutterComponentEx.width
    }

    private fun convert(event: MouseEvent): MouseEvent {
      val y = if (event.isPopupTrigger) {
        val point = event.locationOnScreen
        SwingUtilities.convertPointFromScreen(point, editor.gutterComponentEx)
        point.y
      } else {
        editor.visualLineToY(primaryVisualLine) + event.y
      }
      return MyMouseEvent(event, editor.gutterComponentEx, y)
    }

    private fun throwUnhandledEvent(event: MouseEvent) {
      throw IllegalArgumentException("unhandled event $event")
    }
  }
}
