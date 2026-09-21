// SPDX-License-Identifier: AGPL-3.0-or-later
import type { WebQuickResult } from '../diagnostics/quick'
import type { Ndt7Result } from '../diagnostics/ndt7'
import type { WebStabilityResult } from '../diagnostics/stability'

export function downloadWebReport(
  quick: WebQuickResult | null,
  speed: Ndt7Result | null,
  stability: WebStabilityResult | null,
) {
  const now = new Date().toISOString()
  const stages = []

  if (quick) {
    stages.push({
      id: 'device',
      status: quick.online ? 'ok' : 'problem',
      metrics: { browserOnline: quick.online },
    })

    const internetStatus = quick.findings.some(f => f.severity === 'problem')
      ? 'problem'
      : quick.findings.some(f => f.severity === 'warning') ? 'warning' : 'info'

    stages.push({
      id: 'internet',
      status: internetStatus,
      metrics: {
        httpsReachable: quick.httpsReachable,
        browserRequestMs: quick.latencyMs,
        downloadMbps: speed?.downloadMbps ?? null,
        uploadMbps: speed?.uploadMbps ?? null,
      },
    })
  } else if (speed) {
    stages.push({
      id: 'internet',
      status: 'info',
      metrics: {
        downloadMbps: speed.downloadMbps,
        uploadMbps: speed.uploadMbps,
      },
    })
  }

  if (stability) {
    stages.push({
      id: 'internet',
      status: stability.failurePercent >= 20
        ? 'problem'
        : stability.failurePercent > 0 ? 'warning' : 'info',
      metrics: {
        browserStabilitySampleCount: stability.sampleCount,
        browserFailurePercent: stability.failurePercent,
        averageBrowserRequestMs: stability.averageRequestMs,
        browserJitterMs: stability.jitterMs,
      },
    })
  }

  const report = {
    schemaVersion: '0.1.0',
    id: crypto.randomUUID(),
    mode: stability ? 'stability' : speed ? 'standard' : 'quick',
    platform: 'web',
    startedAt: now,
    endedAt: now,
    stages,
    findings: quick?.findings ?? [],
    metadata: speed ? {
      measurementProvider: 'Measurement Lab',
      ndt7Machine: speed.machine,
      ndt7City: speed.city ?? null,
      ndt7Country: speed.country ?? null,
    } : undefined,
  }

  const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' })
  const href = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = href
  a.download = 'netsira-report-' + new Date().toISOString().replace(/[:.]/g, '-') + '.json'
  a.click()
  URL.revokeObjectURL(href)
}
