// SPDX-License-Identifier: AGPL-3.0-or-later
type Located = {
  downloadUrl: string
  uploadUrl: string
  machine: string
  city?: string
  country?: string
}

export type Ndt7Result = {
  downloadMbps: number
  uploadMbps: number
  machine: string
  city?: string
  country?: string
}

const subprotocol = 'net.measurementlab.ndt.v7'

async function locate(): Promise<Located> {
  const response = await fetch('https://locate.measurementlab.net/v2/nearest/ndt/ndt7', { cache: 'no-store' })
  if (!response.ok) throw new Error('M-Lab locate failed: HTTP ' + response.status)
  const data = await response.json()
  const first = data.results?.[0]
  if (!first) throw new Error('M-Lab returned no available NDT7 server')
  return {
    downloadUrl: first.urls['wss:///ndt/v7/download'],
    uploadUrl: first.urls['wss:///ndt/v7/upload'],
    machine: first.machine ?? 'unknown',
    city: first.location?.city,
    country: first.location?.country,
  }
}

function mbps(bytes: number, milliseconds: number) {
  return milliseconds <= 0 ? 0 : (bytes * 8) / (milliseconds / 1000) / 1_000_000
}

async function download(url: string): Promise<number> {
  return await new Promise((resolve, reject) => {
    const ws = new WebSocket(url, subprotocol)
    ws.binaryType = 'arraybuffer'
    let bytes = 0
    let started = 0
    const timeout = window.setTimeout(() => {
      ws.close()
      reject(new Error('NDT7 download timed out'))
    }, 16_000)

    ws.onopen = () => { started = performance.now() }
    ws.onmessage = (event) => {
      if (event.data instanceof ArrayBuffer) bytes += event.data.byteLength
      else if (event.data instanceof Blob) bytes += event.data.size
    }
    ws.onerror = () => {
      window.clearTimeout(timeout)
      reject(new Error('NDT7 download WebSocket failed'))
    }
    ws.onclose = () => {
      window.clearTimeout(timeout)
      if (!started) return reject(new Error('NDT7 download closed before starting'))
      resolve(mbps(bytes, performance.now() - started))
    }
  })
}

async function upload(url: string): Promise<number> {
  return await new Promise((resolve, reject) => {
    const ws = new WebSocket(url, subprotocol)
    const payload = new Uint8Array(64 * 1024)
    crypto.getRandomValues(payload)
    let bytes = 0
    let started = 0
    let done = false
    const hardTimeout = window.setTimeout(() => finish(), 12_000)

    function finish() {
      if (done) return
      done = true
      window.clearTimeout(hardTimeout)
      const elapsed = performance.now() - started
      if (ws.readyState === WebSocket.OPEN) ws.close(1000, 'complete')
      resolve(mbps(bytes, elapsed))
    }

    function pump() {
      if (done || ws.readyState !== WebSocket.OPEN) return
      if (performance.now() - started >= 10_000) return finish()
      if (ws.bufferedAmount < 4 * 1024 * 1024) {
        ws.send(payload)
        bytes += payload.byteLength
      }
      window.setTimeout(pump, 0)
    }

    ws.onopen = () => {
      started = performance.now()
      pump()
    }
    ws.onerror = () => {
      if (done) return
      done = true
      window.clearTimeout(hardTimeout)
      reject(new Error('NDT7 upload WebSocket failed'))
    }
    ws.onclose = () => {
      if (!done && started) finish()
    }
  })
}

export async function runNdt7(): Promise<Ndt7Result> {
  const server = await locate()
  const downloadMbps = await download(server.downloadUrl)
  const uploadMbps = await upload(server.uploadUrl)
  return { ...server, downloadMbps, uploadMbps }
}
