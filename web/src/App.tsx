// SPDX-License-Identifier: AGPL-3.0-or-later
import { useState } from 'react'
import { IconActivityHeartbeat, IconAntennaBars5, IconCalculator, IconDownload, IconHistory, IconSettings } from '@tabler/icons-react'
import { runWebQuickTest, type WebQuickResult } from './diagnostics/quick'
import { runNdt7, type Ndt7Result } from './diagnostics/ndt7'
import { downloadWebReport } from './reports/export'
import { fsplDb } from './core/rf'

const nav = [
  ['Diagnostics', IconActivityHeartbeat],
  ['Devices', IconAntennaBars5],
  ['Calculators', IconCalculator],
  ['History', IconHistory],
  ['Settings', IconSettings],
] as const

export function App() {
  const [quick, setQuick] = useState('Not run yet.')
  const [quickRunning, setQuickRunning] = useState(false)
  const [lastQuick, setLastQuick] = useState<WebQuickResult | null>(null)
  const [speed, setSpeed] = useState('Not run yet.')
  const [speedRunning, setSpeedRunning] = useState(false)
  const [lastSpeed, setLastSpeed] = useState<Ndt7Result | null>(null)
  const [distance, setDistance] = useState('1')
  const [frequency, setFrequency] = useState('5800')
  const [fspl, setFspl] = useState('—')

  async function runQuick() {
    setQuickRunning(true)
    setQuick('Running…')
    const r = await runWebQuickTest()
    setLastQuick(r)
    setQuick([
      'Browser online: ' + (r.online ? 'yes' : 'no'),
      'HTTPS reachability: ' + (r.httpsReachable ? 'ok' : 'failed'),
      'Request time: ' + (r.latencyMs === null ? 'unavailable' : r.latencyMs.toFixed(1) + ' ms'),
      ...r.findings.map((finding) => "• " + finding.title),
    ].join('\n'))
    setQuickRunning(false)
  }

  async function runSpeed() {
    setSpeedRunning(true)
    setSpeed('Locating M-Lab server and running download/upload…')
    try {
      const r = await runNdt7()
      setLastSpeed(r)
      const location = [r.city, r.country].filter(Boolean).join(', ')
      setSpeed(
        'Download: ' + r.downloadMbps.toFixed(2) + ' Mb/s\n' +
        'Upload: ' + r.uploadMbps.toFixed(2) + ' Mb/s\n' +
        'Server: ' + r.machine + (location ? ' (' + location + ')' : '')
      )
    } catch (error) {
      setSpeed('NDT7 test failed: ' + (error instanceof Error ? error.message : 'unknown error'))
    } finally {
      setSpeedRunning(false)
    }
  }

  function calculate() {
    try { setFspl(fsplDb(Number(distance), Number(frequency)).toFixed(2) + ' dB') }
    catch { setFspl('Invalid values') }
  }

  return (
    <div className="page">
      <aside className="navbar navbar-vertical navbar-expand-lg" data-bs-theme="dark">
        <div className="container-fluid">
          <div className="navbar-brand navbar-brand-autodark">Netsira</div>
          <div className="navbar-nav">
            {nav.map(([label, Icon], index) => (
              <div className="nav-item" key={label}>
                <a className={'nav-link ' + (index === 0 ? 'active' : '')} href="#" onClick={(e) => e.preventDefault()}>
                  <span className="nav-link-icon d-md-none d-lg-inline-block"><Icon size={20} /></span>
                  <span className="nav-link-title">{label}</span>
                </a>
              </div>
            ))}
          </div>
        </div>
      </aside>

      <div className="page-wrapper">
        <div className="page-header d-print-none"><div className="container-xl">
          <div className="d-flex align-items-center justify-content-between gap-3">
            <div>
              <h2 className="page-title">Diagnostics</h2>
              <div className="text-secondary mt-1">The web edition only runs diagnostics browsers can safely expose.</div>
            </div>
            <button className="btn btn-outline-primary" disabled={!lastQuick && !lastSpeed} onClick={() => downloadWebReport(lastQuick, lastSpeed)}>
              <IconDownload size={18} className="me-2" />Export report
            </button>
          </div>
        </div></div>

        <div className="page-body"><div className="container-xl"><div className="row row-cards">
          <div className="col-lg-6"><div className="card h-100"><div className="card-body">
            <h3 className="card-title">Quick test</h3>
            <button className="btn btn-primary mb-3" disabled={quickRunning} onClick={runQuick}>{quickRunning ? 'Running…' : 'Run quick test'}</button>
            <pre className="diagnostic-output">{quick}</pre>
          </div></div></div>

          <div className="col-lg-6"><div className="card h-100"><div className="card-body">
            <h3 className="card-title">Internet throughput (M-Lab NDT7)</h3>
            <p className="text-secondary">Optional. Measurement Lab records measurement metadata including your public IP address.</p>
            <button className="btn btn-primary mb-3" disabled={speedRunning} onClick={runSpeed}>{speedRunning ? 'Running…' : 'Run download + upload test'}</button>
            <pre className="diagnostic-output">{speed}</pre>
          </div></div></div>

          <div className="col-lg-6"><div className="card h-100"><div className="card-body">
            <h3 className="card-title">FSPL calculator</h3>
            <div className="mb-3"><label className="form-label">Distance (km)</label><input className="form-control" value={distance} onChange={(e) => setDistance(e.target.value)} inputMode="decimal" /></div>
            <div className="mb-3"><label className="form-label">Frequency (MHz)</label><input className="form-control" value={frequency} onChange={(e) => setFrequency(e.target.value)} inputMode="decimal" /></div>
            <button className="btn btn-primary" onClick={calculate}>Calculate</button>
            <div className="mt-3"><strong>Result:</strong> {fspl}</div>
          </div></div></div>
        </div></div></div>
      </div>
    </div>
  )
}
