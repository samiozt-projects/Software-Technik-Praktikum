import korlibs.render.GameWindow
import korlibs.render.awt.AwtGameWindow
import korlibs.render.platform.glContextFromComponent
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicReference

/** Remembers the windowed bounds so leaving fullscreen restores the previous size and position. */
private val windowedBounds = AtomicReference<Rectangle?>()

/**
 * Toggles real (borderless) fullscreen on the AWT/JVM backend.
 *
 * KorGE's AWT [GameWindow] implements `fullscreen = true` through Java's exclusive-fullscreen mode
 * (`GraphicsDevice.fullScreenWindow`). On Windows that only *simulates* fullscreen for a decorated
 * frame: the window grows to cover the screen (hiding the taskbar) but keeps its title bar. AWT only
 * lets us drop the title bar while the frame is not displayable, so we dispose the frame, flip
 * [java.awt.Frame.setUndecorated], show it again, and then enter exclusive fullscreen on the now
 * undecorated frame.
 *
 * Re-creating the frame's native peer also invalidates the OpenGL context, so the context has to be
 * cycled around the window changes. Order matters in both directions and is the subtle part:
 *
 *  - The old context is disposed *before* [java.awt.Window.dispose] destroys the peer. Disposing it
 *    afterwards would run `wglMakeCurrent`/`ReleaseDC` against a dead device context and log
 *    `WGL.wglMakeCurrent(...).error = 6` (ERROR_INVALID_HANDLE).
 *  - The new context is created *after* the new peer exists and is then signalled with
 *    [korlibs.graphics.AG.contextLost]. Creating it (`Win32OpenglContext.init()`) makes it current,
 *    so `contextLost()` — which probes GL extensions and resets shader programs and GL objects — runs
 *    against a valid current context. Signalling the loss with no current context (e.g. a bare
 *    `ctx = null` rebuild) leaves KorGE's GL object tracking inconsistent and progressively corrupts
 *    the dynamically rebuilt glyph atlas (the text) across repeated toggles.
 *
 * This mirrors the cycle KorGE's own GLCanvas backend performs on `removeNotify`/`addNotify`.
 *
 * Disposing the frame is safe for the application lifecycle: KorGE only quits on `windowClosing`
 * (the user pressing the close button / Alt+F4), which a programmatic [java.awt.Window.dispose] does
 * not raise.
 *
 * Non-AWT backends remove decoration correctly by themselves, so for them the plain property toggle
 * is enough.
 */
fun GameWindow.toggleBorderlessFullscreen() {
    val awt = this as? AwtGameWindow ?: run {
        fullscreen = !fullscreen
        return
    }

    EventQueue.invokeLater {
        val frame = awt.frame
        val device = frame.graphicsConfiguration?.device
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val enterFullscreen = !frame.isUndecorated

        // Tear down the GL context while the current peer (and its device context) is still alive.
        awt.ctx?.dispose()
        awt.ctx = null

        if (enterFullscreen) {
            windowedBounds.set(frame.bounds)
            frame.dispose()
            frame.isUndecorated = true
            frame.isVisible = true
            device.fullScreenWindow = frame
        } else {
            device.fullScreenWindow = null
            frame.dispose()
            frame.isUndecorated = false
            windowedBounds.get()?.let { frame.bounds = it }
            frame.isVisible = true
        }
        frame.requestFocus()

        // Build a fresh context for the new peer, then signal the loss so KorGE re-uploads everything.
        awt.ctx = glContextFromComponent(frame, awt)
        awt.ag.contextLost()
    }
}
