package com.zynergylabs.forager.app.ui.log.scratch

import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import java.io.File
import java.lang.reflect.Field
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext
import androidx.compose.ui.unit.Constraints

/**
 * SCRATCH, never merged (dispatch 2026-09-28-348). Test-only recorder for the thread an apply or a
 * measure runs on, and the state of the Compose test harness's `isDeferringContinuations` flag at
 * that moment. No production file is touched. Observing changes timing, so a result is about the
 * probed build, not about the unprobed one.
 */
object ThreadProbe {
    private val dir = File(System.getenv("DPT_LOG_DIR") ?: "/tmp/dpt-probe").apply { mkdirs() }
    private val file = File(dir, "probe.log")
    @Volatile private var interceptor: Any? = null
    @Volatile private var flagField: Field? = null
    @Volatile var currentTest: String = "?"

    fun begin(test: String) { currentTest = test; interceptor = null; flagField = null; line("BEGIN") }
    fun end(failed: Boolean) = line(if (failed) "END failed" else "END ok")

    /** Reads the harness flag through reflection on the interceptor chain, null when not found. */
    fun deferring(): String {
        val f = flagField ?: return "unknown"
        return try { f.getBoolean(interceptor).toString() } catch (t: Throwable) { "err:${t.javaClass.simpleName}" }
    }

    fun capture(i: ContinuationInterceptor?) {
        var found: Any? = null
        fun walk(o: Any?, depth: Int) {
            if (o == null || found != null || depth > 5) return
            if (o.javaClass.name.endsWith("FrameDeferringContinuationInterceptor")) { found = o; return }
            var c: Class<*>? = o.javaClass
            while (c != null) {
                for (fl in c.declaredFields) {
                    if (ContinuationInterceptor::class.java.isAssignableFrom(fl.type) || fl.type == Any::class.java) {
                        fl.isAccessible = true
                        runCatching { walk(fl.get(o), depth + 1) }
                    }
                }
                c = c.superclass
            }
        }
        walk(i, 0)
        val o = found
        if (o != null) {
            flagField = o.javaClass.getDeclaredField("isDeferringContinuations").apply { isAccessible = true }
            interceptor = o
        }
        line("CAPTURE interceptor=${o?.javaClass?.simpleName} via=${i?.javaClass?.simpleName}")
    }

    fun record(event: String) = line(event)

    @Synchronized
    private fun line(event: String) {
        val t = Thread.currentThread()
        val main = Looper.getMainLooper().isCurrentThread
        val off = if (!main && event != "BEGIN" && !event.startsWith("END") && !event.startsWith("CAPTURE")) " OFFMAIN" else ""
        file.appendText("${System.nanoTime()} [$currentTest] $event thread=${t.name} main=$main deferring=${deferring()}$off\n")
    }
}

@Composable
fun ProbeCapture() {
    LaunchedEffect(Unit) { ThreadProbe.capture(coroutineContext[ContinuationInterceptor]) }
}

/** Equals is always false so that Compose calls [update] on every apply of the chain it sits in. */
fun Modifier.probe(tag: String): Modifier = this.then(ProbeElement(tag))

private class ProbeElement(private val tag: String) : ModifierNodeElement<ProbeNode>() {
    override fun create(): ProbeNode { ThreadProbe.record("apply:create:$tag"); return ProbeNode(tag) }
    override fun update(node: ProbeNode) { ThreadProbe.record("apply:update:$tag") }
    override fun equals(other: Any?): Boolean = false
    override fun hashCode(): Int = System.identityHashCode(this)
}

private class ProbeNode(private val tag: String) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        ThreadProbe.record("measure:$tag")
        val p = measurable.measure(constraints)
        return layout(p.width, p.height) { p.place(0, 0) }
    }
}
