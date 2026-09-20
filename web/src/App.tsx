// SPDX-License-Identifier: AGPL-3.0-or-later
import {
  IconActivityHeartbeat,
  IconAntennaBars5,
  IconCalculator,
  IconHistory,
  IconSettings,
} from '@tabler/icons-react'

const nav = [
  ['Diagnostics', IconActivityHeartbeat],
  ['Devices', IconAntennaBars5],
  ['Calculators', IconCalculator],
  ['History', IconHistory],
  ['Settings', IconSettings],
] as const

export function App() {
  return (
    <div className="page">
      <aside className="navbar navbar-vertical navbar-expand-lg" data-bs-theme="dark">
        <div className="container-fluid">
          <div className="navbar-brand navbar-brand-autodark">Netsira</div>
          <div className="navbar-nav">
            {nav.map(([label, Icon], index) => (
              <div className="nav-item" key={label}>
                <a className={`nav-link ${index === 0 ? 'active' : ''}`} href="#" onClick={(e) => e.preventDefault()}>
                  <span className="nav-link-icon d-md-none d-lg-inline-block"><Icon size={20} /></span>
                  <span className="nav-link-title">{label}</span>
                </a>
              </div>
            ))}
          </div>
        </div>
      </aside>

      <div className="page-wrapper">
        <div className="page-header d-print-none">
          <div className="container-xl">
            <h2 className="page-title">Diagnostics</h2>
            <div className="text-secondary mt-1">
              Browser-safe diagnostics and calculators. Native apps provide deeper LAN and CPE access.
            </div>
          </div>
        </div>

        <div className="page-body">
          <div className="container-xl">
            <div className="row row-cards">
              <div className="col-md-6">
                <div className="card">
                  <div className="card-body">
                    <h3 className="card-title">Internet test</h3>
                    <p className="text-secondary">Latency, jitter, loss and public throughput measurement arrive in the next milestone.</p>
                    <button className="btn btn-primary" disabled>Phase 2</button>
                  </div>
                </div>
              </div>
              <div className="col-md-6">
                <div className="card">
                  <div className="card-body">
                    <h3 className="card-title">RF calculators</h3>
                    <p className="text-secondary">FSPL, Fresnel, EIRP, link budget and dBm/mW share the same rules as native apps.</p>
                    <button className="btn btn-primary" disabled>Phase 2</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
