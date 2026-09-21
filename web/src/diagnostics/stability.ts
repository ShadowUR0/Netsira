// SPDX-License-Identifier: AGPL-3.0-or-later
export type WebStabilitySample = {
  ok: boolean
  requestMs: number | null
}

export type WebStabilityResult = {
  sampleCount: number
  failurePercent: number
  averageRequestMs: number | null
  jitterMs: number | null
}

export async function runWebStability(
  signal: AbortSignal,
  onSample: (sample: WebStabilitySample, index: number) => void,
  durationMs = 30_000,
  intervalMs = 1_000,
): Promise<WebStabilityResult> {
  const samples: WebStabilitySample[] = []
  const started = performance.now()

  while (performance.now() - started < durationMs) {
    if (signal.aborted) throw new DOMException('Cancelled', 'AbortError')

    const sampleStarted = performance.now()
    let sample: WebStabilitySample
    try {
      const response = await fetch('https://locate.measurementlab.net/v2/nearest/ndt/ndt7', {
        cache: 'no-store',
        signal,
      })
      sample = {
        ok: response.ok,
        requestMs: response.ok ? performance.now() - sampleStarted : null,
      }
    } catch (error) {
      if (signal.aborted) throw error
      sample = { ok: false, requestMs: null }
    }

    samples.push(sample)
    onSample(sample, samples.length)

    const elapsed = performance.now() - sampleStarted
    const sleep = Math.max(0, intervalMs - elapsed)
    if (sleep > 0) await abortableDelay(sleep, signal)
  }

  return summarizeWebStability(samples)
}

export function summarizeWebStability(samples: WebStabilitySample[]): WebStabilityResult {
  const successful = samples.flatMap(sample => sample.requestMs === null ? [] : [sample.requestMs])
  const deltas = successful.slice(1).map((value, index) => Math.abs(value - successful[index]))

  return {
    sampleCount: samples.length,
    failurePercent: samples.length === 0 ? 0 : ((samples.length - successful.length) * 100) / samples.length,
    averageRequestMs: successful.length === 0 ? null : successful.reduce((a, b) => a + b, 0) / successful.length,
    jitterMs: deltas.length === 0 ? null : deltas.reduce((a, b) => a + b, 0) / deltas.length,
  }
}

function abortableDelay(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(resolve, ms)
    signal.addEventListener('abort', () => {
      window.clearTimeout(timer)
      reject(new DOMException('Cancelled', 'AbortError'))
    }, { once: true })
  })
}
