// SPDX-License-Identifier: AGPL-3.0-or-later
export type WebQuickResult = {
  online: boolean
  httpsReachable: boolean
  latencyMs: number | null
  finding: string
}

export async function runWebQuickTest(): Promise<WebQuickResult> {
  const online = navigator.onLine
  const started = performance.now()
  try {
    const response = await fetch('https://locate.measurementlab.net/v2/nearest/ndt/ndt7', { cache: 'no-store' })
    const latencyMs = performance.now() - started
    if (!response.ok) throw new Error('HTTP ' + response.status)
    return {
      online,
      httpsReachable: true,
      latencyMs,
      finding: latencyMs >= 1000
        ? 'Warning: the browser HTTPS reachability check was slow.'
        : 'No obvious problem was found by the browser-safe quick test.',
    }
  } catch {
    return {
      online,
      httpsReachable: false,
      latencyMs: null,
      finding: online
        ? 'Problem: the browser reports a network, but the external HTTPS check failed.'
        : 'Problem: the browser reports that it is offline.',
    }
  }
}
