// SPDX-License-Identifier: AGPL-3.0-or-later
import { useRef, useState, type ReactNode } from 'react'
import {
  IconActivityHeartbeat,
  IconAntennaBars5,
  IconCalculator,
  IconDownload,
  IconHistory,
  IconSettings,
  IconGauge,
  IconWorld,
  IconWifi,
  IconShieldCheck,
  IconChevronRight,
} from '@tabler/icons-react'
import { runWebQuickTest, type WebQuickResult } from './diagnostics/quick'
import { runNdt7, type Ndt7Result } from './diagnostics/ndt7'
import { runWebStability, type WebStabilityResult } from './diagnostics/stability'
import { downloadWebReport } from './reports/export'
import { fsplDb } from './core/rf'

type Page = 'diagnostics' | 'devices' | 'calculators' | 'history' | 'settings'

const nav: Array<[Page, string, typeof IconActivityHeartbeat]> = [
  ['diagnostics', 'Diagnostics', IconActivityHeartbeat],
  ['devices', 'Devices', IconAntennaBars5],
  ['calculators', 'Calculators', IconCalculator],
  ['history', 'History', IconHistory],
  ['settings', 'Settings', IconSettings],
]

function MetricCard({
  label,
  value,
  hint,
  icon,
  state = 'neutral',
  progress,
}: {
  label: string
  value: string
  hint?: string
  icon: ReactNode
  state?: 'good' | 'warning' | 'problem' | 'neutral'
  progress?: number
}) {
  return (
    <div className={'metric-card metric-' + state}>
      <div className="metric-icon">{icon}</div>
      <div>
        <div className="metric-label">{label}</div>
        <div className="metric-value">{value}</div>
        {typeof progress === 'number' && (
          <div className="metric-progress" aria-hidden="true">
            <span style={{ width: Math.max(0, Math.min(100, progress * 100)) + '%' }} />
          </div>
        )}
        {hint && <div className="metric-hint">{hint}</div>}
      </div>
    </div>
  )
}

function StatusPill({ state, children }: { state: 'good' | 'warning' | 'problem' | 'neutral'; children: ReactNode }) {
  return <span className={'status-pill status-' + state}>{children}</span>
}

export function App() {
  const [page, setPage] = useState<Page>('diagnostics')
  const [mlabConsent, setMlabConsent] = useState(false)
  const [modeRunning, setModeRunning] = useState(false)
  const [modeText, setModeText] = useState('Not run yet.')
  const [quickRunning, setQuickRunning] = useState(false)
  const [lastQuick, setLastQuick] = useState<WebQuickResult | null>(null)
  const [speedRunning, setSpeedRunning] = useState(false)
  const [lastSpeed, setLastSpeed] = useState<Ndt7Result | null>(null)
  const [stabilityRunning, setStabilityRunning] = useState(false)
  const [lastStability, setLastStability] = useState<WebStabilityResult | null>(null)
  const [stabilityText, setStabilityText] = useState('Not run yet.')
  const stabilityAbort = useRef<AbortController | null>(null)
  const [distance, setDistance] = useState('1')
  const [frequency, setFrequency] = useState('5800')
  const [fspl, setFspl] = useState('—')

  async function runQuick() {
    setQuickRunning(true)
    try {
      setLastQuick(await runWebQuickTest())
    } finally {
      setQuickRunning(false)
    }
  }

  async function runStandard() {
    if (!mlabConsent) {
      setModeText('Confirm the M-Lab privacy notice first.')
      return
    }
    setModeRunning(true)
    setModeText('Running connection checks and throughput test…')
    try {
      const quickResult = await runWebQuickTest()
      setLastQuick(quickResult)
      const speedResult = await runNdt7()
      setLastSpeed(speedResult)
      setModeText('Standard test complete.')
    } catch (error) {
      setModeText('Test failed: ' + (error instanceof Error ? error.message : 'unknown error'))
    } finally {
      setModeRunning(false)
    }
  }

  async function runSpeed() {
    if (!mlabConsent) {
      setModeText('Confirm the M-Lab privacy notice first.')
      return
    }
    setSpeedRunning(true)
    try {
      setLastSpeed(await runNdt7())
    } finally {
      setSpeedRunning(false)
    }
  }

  async function runStability() {
    const controller = new AbortController()
    stabilityAbort.current?.abort()
    stabilityAbort.current = controller
    setStabilityRunning(true)
    setStabilityText('Starting 30-second browser stability test…')

    try {
      const result = await runWebStability(
        controller.signal,
        (sample, index) => {
          setStabilityText(
            'Sample ' + index + ' · HTTPS ' +
            (sample.ok && sample.requestMs !== null ? sample.requestMs.toFixed(0) + ' ms' : 'failed'),
          )
        },
      )
      setLastStability(result)
      setStabilityText('Stability test complete.')
    } catch (error) {
      setStabilityText(
        error instanceof DOMException && error.name === 'AbortError'
          ? 'Stability test cancelled.'
          : 'Stability test failed.',
      )
    } finally {
      setStabilityRunning(false)
      if (stabilityAbort.current === controller) stabilityAbort.current = null
    }
  }

  function calculate() {
    try {
      setFspl(fsplDb(Number(distance), Number(frequency)).toFixed(2) + ' dB')
    } catch {
      setFspl('Invalid values')
    }
  }

  const quickProblem = lastQuick?.findings.some(f => f.severity === 'problem') ?? false
  const quickWarning = lastQuick?.findings.some(f => f.severity === 'warning') ?? false
  const overallState = !lastQuick ? 'neutral' : quickProblem ? 'problem' : quickWarning ? 'warning' : 'good'
  const overallLabel = !lastQuick ? 'Not tested' : quickProblem ? 'Problem found' : quickWarning ? 'Needs attention' : 'Looks good'

  return (
    <div className="netsira-shell">
      <aside className="app-sidebar">
        <div className="brand-block">
          <div className="brand-mark">N</div>
          <div>
            <div className="brand-title">Netsira</div>
            <div className="brand-subtitle">Network diagnostics</div>
          </div>
        </div>

        <nav className="app-nav" aria-label="Primary">
          {nav.map(([id, label, Icon]) => (
            <button
              key={id}
              type="button"
              className={'nav-button ' + (page === id ? 'active' : '')}
              aria-current={page === id ? 'page' : undefined}
              onClick={() => setPage(id)}
            >
              <Icon size={20} stroke={1.8} />
              <span>{label}</span>
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <IconShieldCheck size={18} />
          <span>Local-first · no telemetry</span>
        </div>
      </aside>

      <main className="app-main">
        {page === 'diagnostics' && (
          <>
            <header className="page-heading">
              <div>
                <div className="eyebrow">NETWORK HEALTH</div>
                <h1>Diagnostics</h1>
                <p>Start with a quick check. Open advanced tests only when you need them.</p>
              </div>
              <button
                className="btn btn-outline-primary export-button"
                disabled={!lastQuick && !lastSpeed && !lastStability}
                onClick={() => downloadWebReport(lastQuick, lastSpeed, lastStability)}
              >
                <IconDownload size={18} /> Export report
              </button>
            </header>

            <section className="health-hero">
              <div>
                <StatusPill state={overallState}>{overallLabel}</StatusPill>
                <h2>{!lastQuick ? 'Run a quick check to see what is healthy.' : lastQuick.findings[0]?.title ?? 'Check complete.'}</h2>
                <p>The browser can check internet reachability and stability. Deep LAN/CPE checks require the native app.</p>
              </div>
              <button className="primary-action" disabled={quickRunning} onClick={runQuick}>
                <IconGauge size={22} />
                <span>
                  <strong>{quickRunning ? 'Checking…' : 'Run quick check'}</strong>
                  <small>Usually finishes in a few seconds</small>
                </span>
                <IconChevronRight size={20} />
              </button>
            </section>

            <section className="connection-path" aria-label="Connection path">
              <div className="path-node path-good">
                <span>1</span><div><b>Browser</b><small>Ready</small></div>
              </div>
              <div className="path-line" />
              <div className={'path-node ' + (!lastQuick ? 'path-neutral' : lastQuick.online ? 'path-good' : 'path-problem')}>
                <span>2</span><div><b>Network</b><small>{!lastQuick ? 'Not tested' : lastQuick.online ? 'Connected' : 'Problem'}</small></div>
              </div>
              <div className="path-line" />
              <div className={'path-node ' + (!lastQuick ? 'path-neutral' : lastQuick.httpsReachable ? 'path-good' : 'path-problem')}>
                <span>3</span><div><b>Internet</b><small>{!lastQuick ? 'Not tested' : lastQuick.httpsReachable ? 'Reachable' : 'Problem'}</small></div>
              </div>
            </section>

            <section className="metric-grid" aria-label="Latest results">
              <MetricCard
                label="Internet"
                value={!lastQuick ? 'Not tested' : lastQuick.httpsReachable ? 'Reachable' : 'Unavailable'}
                hint={lastQuick?.latencyMs == null ? 'HTTPS reachability' : lastQuick.latencyMs.toFixed(0) + ' ms request'}
                icon={<IconWorld size={22} />}
                state={!lastQuick ? 'neutral' : lastQuick.httpsReachable ? 'good' : 'problem'}
                progress={lastQuick ? (lastQuick.httpsReachable ? 1 : 0) : undefined}
              />
              <MetricCard
                label="Download"
                value={lastSpeed ? lastSpeed.downloadMbps.toFixed(1) + ' Mb/s' : '—'}
                hint="M-Lab throughput"
                icon={<IconDownload size={22} />}
                state={lastSpeed ? 'good' : 'neutral'}
              />
              <MetricCard
                label="Stability"
                value={lastStability ? (100 - lastStability.failurePercent).toFixed(0) + '%' : '—'}
                hint={lastStability ? 'Successful checks' : '30-second test'}
                icon={<IconActivityHeartbeat size={22} />}
                state={!lastStability ? 'neutral' : lastStability.failurePercent > 0 ? 'warning' : 'good'}
                progress={lastStability ? Math.max(0, Math.min(1, (100 - lastStability.failurePercent) / 100)) : undefined}
              />
              <MetricCard
                label="Device / CPE"
                value="Native app"
                hint="Signal, SNR, Ethernet and alignment"
                icon={<IconWifi size={22} />}
              />
            </section>

            {lastQuick && (
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>What Netsira found</h2>
                    <p>Important findings first; raw measurements stay secondary.</p>
                  </div>
                </div>
                <div className="finding-list">
                  {lastQuick.findings.map((finding) => (
                    <div className={'finding-row finding-' + finding.severity} key={finding.code}>
                      <StatusPill state={finding.severity === 'problem' ? 'problem' : finding.severity === 'warning' ? 'warning' : 'good'}>
                        {finding.severity === 'problem' ? 'Problem' : finding.severity === 'warning' ? 'Attention' : 'OK'}
                      </StatusPill>
                      <span>{finding.title}</span>
                    </div>
                  ))}
                </div>
              </section>
            )}

            <section className="panel">
              <div className="panel-heading">
                <div>
                  <h2>More tests</h2>
                  <p>Use these when the quick check is not enough.</p>
                </div>
              </div>

              <div className="action-grid">
                <article className="action-card">
                  <IconActivityHeartbeat size={24} />
                  <h3>Stability test</h3>
                  <p>Checks the connection repeatedly for 30 seconds and summarizes failures and timing variation.</p>
                  <div className="button-row">
                    <button className="btn btn-primary" disabled={stabilityRunning} onClick={runStability}>
                      {stabilityRunning ? 'Running…' : 'Run stability test'}
                    </button>
                    <button className="btn btn-outline-secondary" disabled={!stabilityRunning} onClick={() => stabilityAbort.current?.abort()}>
                      Cancel
                    </button>
                  </div>
                  {lastStability && (
                    <>
                    <div className="quality-bar" aria-label="Stability success rate">
                      <span style={{ width: Math.max(0, Math.min(100, 100 - lastStability.failurePercent)) + '%' }} />
                    </div>
                    <div className="mini-metrics">
                      <span><b>{lastStability.failurePercent.toFixed(1)}%</b> failures</span>
                      <span><b>{lastStability.averageRequestMs?.toFixed(0) ?? '—'} ms</b> avg</span>
                      <span><b>{lastStability.jitterMs?.toFixed(0) ?? '—'} ms</b> jitter</span>
                    </div>
                    </>
                  )}
                  {!lastStability && <small className="muted">{stabilityText}</small>}
                </article>

                <article className="action-card">
                  <IconGauge size={24} />
                  <h3>Internet speed</h3>
                  <p>Measures download and upload using Measurement Lab. This sends measurement metadata to M-Lab.</p>
                  <label className="consent-row">
                    <input type="checkbox" checked={mlabConsent} onChange={(e) => setMlabConsent(e.target.checked)} />
                    <span>I understand the M-Lab privacy notice</span>
                  </label>
                  <div className="button-row">
                    <button className="btn btn-primary" disabled={speedRunning} onClick={runSpeed}>
                      {speedRunning ? 'Running…' : 'Run speed test'}
                    </button>
                    <button className="btn btn-outline-primary" disabled={modeRunning} onClick={runStandard}>
                      Standard test
                    </button>
                  </div>
                  {lastSpeed && (
                    <div className="speed-pair">
                      <div><small>Download</small><strong>{lastSpeed.downloadMbps.toFixed(1)}</strong><span>Mb/s</span></div>
                      <div><small>Upload</small><strong>{lastSpeed.uploadMbps.toFixed(1)}</strong><span>Mb/s</span></div>
                    </div>
                  )}
                  {!lastSpeed && <small className="muted">{modeText}</small>}
                </article>
              </div>
            </section>
          </>
        )}

        {page === 'devices' && (
          <>
            <header className="page-heading">
              <div>
                <div className="eyebrow">LOCAL NETWORK</div>
                <h1>Devices</h1>
                <p>Deep CPE discovery and radio diagnostics are available in the Android and Windows apps.</p>
              </div>
            </header>
            <section className="panel native-callout">
              <IconAntennaBars5 size={34} />
              <div>
                <h2>Use a native app for CPE diagnostics</h2>
                <p>Browsers cannot reliably access local airOS devices because of CORS, local-network restrictions, and certificate rules.</p>
                <div className="capability-list">
                  <span>Signal & SNR</span><span>Chain balance</span><span>Ethernet link</span><span>Live alignment</span>
                </div>
              </div>
            </section>
          </>
        )}

        {page === 'calculators' && (
          <>
            <header className="page-heading">
              <div>
                <div className="eyebrow">RF TOOLS</div>
                <h1>Calculators</h1>
                <p>Engineering tools stay separate from diagnostics so you can use them at any time.</p>
              </div>
            </header>
            <section className="panel calculator-panel">
              <div className="panel-heading">
                <div><h2>Free-space path loss</h2><p>Estimate path loss from distance and frequency.</p></div>
                <StatusPill state="neutral">FSPL</StatusPill>
              </div>
              <div className="form-grid">
                <label>
                  <span>Distance</span>
                  <div className="input-with-unit"><input value={distance} onChange={(e) => setDistance(e.target.value)} inputMode="decimal" /><b>km</b></div>
                </label>
                <label>
                  <span>Frequency</span>
                  <div className="input-with-unit"><input value={frequency} onChange={(e) => setFrequency(e.target.value)} inputMode="decimal" /><b>MHz</b></div>
                </label>
              </div>
              <button className="btn btn-primary" onClick={calculate}>Calculate</button>
              <div className="calculator-result"><small>Estimated path loss</small><strong>{fspl}</strong></div>
            </section>
          </>
        )}

        {page === 'history' && (
          <>
            <header className="page-heading">
              <div>
                <div className="eyebrow">PAST TESTS</div>
                <h1>History</h1>
                <p>Saved test history and comparisons are planned for the product-polish phase.</p>
              </div>
            </header>
            <section className="empty-state panel">
              <IconHistory size={36} />
              <h2>No saved runs yet</h2>
              <p>For now, export a JSON report after a test. Persistent history will live here.</p>
            </section>
          </>
        )}

        {page === 'settings' && (
          <>
            <header className="page-heading">
              <div>
                <div className="eyebrow">PRIVACY & BEHAVIOR</div>
                <h1>Settings</h1>
                <p>Netsira is local-first. External measurement is opt-in.</p>
              </div>
            </header>
            <section className="panel settings-list">
              <div className="setting-row">
                <IconShieldCheck size={24} />
                <div><h3>Telemetry</h3><p>No analytics or hidden telemetry.</p></div>
                <StatusPill state="good">Off</StatusPill>
              </div>
              <div className="setting-row">
                <IconWorld size={24} />
                <div><h3>M-Lab throughput</h3><p>Runs only after explicit consent in Diagnostics.</p></div>
                <StatusPill state="neutral">Opt-in</StatusPill>
              </div>
            </section>
          </>
        )}
      </main>
    </div>
  )
}
