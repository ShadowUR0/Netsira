// SPDX-License-Identifier: AGPL-3.0-or-later
export type DiagnosticFinding = {
  code: string
  severity: 'info' | 'warning' | 'problem'
  title: string
  evidence: string[]
}

export type WebQuickResult = {
  online: boolean
  httpsReachable: boolean
  latencyMs: number | null
  findings: DiagnosticFinding[]
}

export async function runWebQuickTest(): Promise<WebQuickResult> {
  const online = navigator.onLine
  const started = performance.now()
  try {
    const response = await fetch(window.location.href, { cache: 'no-store', credentials: 'omit' })
    const latencyMs = performance.now() - started
    if (!response.ok) throw new Error('HTTP ' + response.status)

    const finding: DiagnosticFinding = latencyMs >= 1000
      ? {
          code: 'WEB_HTTPS_SLOW',
          severity: 'warning',
          title: 'The browser HTTPS reachability check was slow.',
          evidence: ['browserRequestMs=' + latencyMs.toFixed(2)],
        }
      : {
          code: 'WEB_NO_OBVIOUS_ISSUE',
          severity: 'info',
          title: 'No obvious problem was found by the browser-safe quick test.',
          evidence: ['browserOnline=' + online, 'browserRequestMs=' + latencyMs.toFixed(2)],
        }

    return { online, httpsReachable: true, latencyMs, findings: [finding] }
  } catch {
    const finding: DiagnosticFinding = online
      ? {
          code: 'WEB_HTTPS_FAILED',
          severity: 'problem',
          title: 'The browser reports a network, but the external HTTPS check failed.',
          evidence: ['browserOnline=true', 'httpsReachable=false'],
        }
      : {
          code: 'NET_NO_ACTIVE',
          severity: 'problem',
          title: 'The browser reports that it is offline.',
          evidence: ['browserOnline=false'],
        }

    return { online, httpsReachable: false, latencyMs: null, findings: [finding] }
  }
}
