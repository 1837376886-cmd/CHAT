export function generateFingerprint() {
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')
  ctx.textBaseline = 'top'
  ctx.font = '14px Arial'
  ctx.fillText('Hello, visitor!', 2, 2)
  const canvasData = canvas.toDataURL()

  const components = [
    navigator.language,
    screen.colorDepth,
    screen.width + 'x' + screen.height,
    new Date().getTimezoneOffset(),
    canvasData,
    navigator.platform,
    navigator.hardwareConcurrency || '',
    navigator.deviceMemory || ''
  ]

  let hash = 0
  const str = components.join('###')
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash
  }
  return 'fp_' + Math.abs(hash).toString(16)
}

export function getOrCreateDeviceFingerprint() {
  let fp = localStorage.getItem('cs_device_fp')
  if (!fp) {
    fp = generateFingerprint()
    localStorage.setItem('cs_device_fp', fp)
  }
  return fp
}
