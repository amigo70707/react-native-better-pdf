/**
 * Measures JS-thread responsiveness with requestAnimationFrame.
 *
 * rAF callbacks run on the JS thread, so gaps between them show when the JS
 * thread was too busy to service a frame. This is the metric that shows
 * whether a viewer does work in JS while the user scrolls or zooms.
 */
export interface JsFrameStats {
  durationMs: number;
  frames: number;
  /** Frames whose interval exceeded 1.5x the median frame interval. */
  droppedFrames: number;
  maxFrameMs: number;
  p95FrameMs: number;
}

export class JsFrameMonitor {
  private intervals: number[] = [];
  private last = 0;
  private start = 0;
  private handle: number | null = null;

  begin() {
    this.intervals = [];
    this.start = performance.now();
    this.last = this.start;
    const tick = (now: number) => {
      this.intervals.push(now - this.last);
      this.last = now;
      this.handle = requestAnimationFrame(tick);
    };
    this.handle = requestAnimationFrame(tick);
  }

  end(): JsFrameStats {
    if (this.handle != null) cancelAnimationFrame(this.handle);
    this.handle = null;
    const durationMs = performance.now() - this.start;
    const sorted = [...this.intervals].sort((a, b) => a - b);
    const median = sorted[Math.floor(sorted.length / 2)] ?? 16.7;
    const frameBudget = Math.max(median, 8);
    return {
      durationMs: round(durationMs),
      frames: this.intervals.length,
      droppedFrames: this.intervals.filter(
        (interval) => interval > frameBudget * 1.5,
      ).length,
      maxFrameMs: round(sorted[sorted.length - 1] ?? 0),
      p95FrameMs: round(sorted[Math.floor(sorted.length * 0.95)] ?? 0),
    };
  }
}

const round = (value: number) => Math.round(value * 10) / 10;
