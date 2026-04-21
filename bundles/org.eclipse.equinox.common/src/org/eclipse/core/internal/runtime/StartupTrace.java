/*******************************************************************************
 * Local-only startup tracer for measuring Eclipse platform startup phases.
 * NOT FOR UPSTREAM MERGE. Always-on; writes to ${user.home}/.eclipse/startup-trace.csv.
 *
 * Lives in equinox.common so it is visible to every downstream bundle that
 * Require-Bundles org.eclipse.equinox.common (directly or via the split package
 * contributed by org.eclipse.core.runtime).
 *******************************************************************************/
package org.eclipse.core.internal.runtime;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Always-on lightweight startup tracer. Uses only {@code java.*} APIs so it is
 * safe to call from the earliest startup phases before any Eclipse plug-in is
 * activated. All call sites share one static queue and one {@code RUN_ID}, so
 * contributions from every bundle end up in a single CSV under one run.
 */
public final class StartupTrace {

	/**
	 * Hardcoded to {@code true} for this tracing build. Exposed so callers may
	 * guard expensive trace-building code, though in practice begin/record are
	 * cheap enough that gating is unnecessary.
	 */
	public static final boolean ENABLED = true;

	private static final Path OUTPUT_DIR = Path.of(System.getProperty("user.home"), ".eclipse"); //$NON-NLS-1$ //$NON-NLS-2$
	private static final Path OUTPUT_CSV = OUTPUT_DIR.resolve("startup-trace.csv"); //$NON-NLS-1$

	private static final ConcurrentLinkedQueue<Entry> ENTRIES = new ConcurrentLinkedQueue<>();
	private static final AtomicLong SEQ = new AtomicLong();
	private static final AtomicBoolean DUMPED = new AtomicBoolean();
	private static final String RUN_ID = Long.toHexString(System.currentTimeMillis()) + "-" //$NON-NLS-1$
			+ Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFL);

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(StartupTrace::dump, "StartupTrace-dump")); //$NON-NLS-1$
		System.out.println("[StartupTrace] enabled, runId=" + RUN_ID); //$NON-NLS-1$
	}

	private StartupTrace() {
	}

	/** Returns a start timestamp (ns). */
	public static long begin() {
		return ENABLED ? System.nanoTime() : 0L;
	}

	/** Records a finished span. */
	public static void record(String phase, long startNanos) {
		if (!ENABLED) {
			return;
		}
		long end = System.nanoTime();
		ENTRIES.add(new Entry(SEQ.getAndIncrement(), phase, startNanos, end, Thread.currentThread().getName()));
	}

	/** Convenience: time a Runnable. */
	public static void time(String phase, Runnable r) {
		if (!ENABLED) {
			r.run();
			return;
		}
		long t = System.nanoTime();
		try {
			r.run();
		} finally {
			record(phase, t);
		}
	}

	/** Force a dump now (also runs automatically on JVM shutdown). */
	public static void dump() {
		if (!DUMPED.compareAndSet(false, true)) {
			return;
		}
		try {
			Files.createDirectories(OUTPUT_DIR);
			boolean writeHeader = !Files.exists(OUTPUT_CSV);
			List<Entry> snapshot = new ArrayList<>(ENTRIES);
			snapshot.sort(Comparator.comparingLong(e -> e.seq));
			try (BufferedWriter w = Files.newBufferedWriter(OUTPUT_CSV, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
				if (writeHeader) {
					w.write("runId,seq,phase,startNs,endNs,durationUs,thread\n"); //$NON-NLS-1$
				}
				for (Entry e : snapshot) {
					long durUs = (e.endNs - e.startNs) / 1000L;
					w.write(RUN_ID);
					w.write(',');
					w.write(Long.toString(e.seq));
					w.write(',');
					w.write(csvEscape(e.phase));
					w.write(',');
					w.write(Long.toString(e.startNs));
					w.write(',');
					w.write(Long.toString(e.endNs));
					w.write(',');
					w.write(Long.toString(durUs));
					w.write(',');
					w.write(csvEscape(e.thread));
					w.write('\n');
				}
			}
			printSummary(snapshot);
		} catch (IOException ex) {
			System.err.println("[StartupTrace] failed to dump: " + ex); //$NON-NLS-1$
		}
	}

	private static void printSummary(List<Entry> snapshot) {
		Map<String, long[]> agg = new HashMap<>();
		for (Entry e : snapshot) {
			long dur = e.endNs - e.startNs;
			long[] a = agg.computeIfAbsent(e.phase, k -> new long[2]);
			a[0] += dur;
			a[1] += 1;
		}
		List<Map.Entry<String, long[]>> sorted = new ArrayList<>(agg.entrySet());
		sorted.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
		System.out.println("[StartupTrace] runId=" + RUN_ID + " entries=" + snapshot.size() + " csv=" + OUTPUT_CSV); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		System.out.println("[StartupTrace] top phases by cumulative time:"); //$NON-NLS-1$
		System.out.printf("  %10s  %5s  %s%n", "cum_ms", "count", "phase"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		int n = Math.min(40, sorted.size());
		for (int i = 0; i < n; i++) {
			Map.Entry<String, long[]> m = sorted.get(i);
			double ms = m.getValue()[0] / 1_000_000.0;
			long count = m.getValue()[1];
			System.out.printf("  %10.3f  %5d  %s%n", ms, count, m.getKey()); //$NON-NLS-1$
		}
	}

	private static String csvEscape(String s) {
		if (s == null) {
			return ""; //$NON-NLS-1$
		}
		if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0) {
			return s;
		}
		return "\"" + s.replace("\"", "\"\"") + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private static final class Entry {
		final long seq;
		final String phase;
		final long startNs;
		final long endNs;
		final String thread;

		Entry(long seq, String phase, long startNs, long endNs, String thread) {
			this.seq = seq;
			this.phase = phase;
			this.startNs = startNs;
			this.endNs = endNs;
			this.thread = thread;
		}
	}
}
