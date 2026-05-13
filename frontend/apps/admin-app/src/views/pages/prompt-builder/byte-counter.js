export function utf8ByteLength(value) {
  if (value === null || value === undefined || value === '') return 0
  return new TextEncoder().encode(String(value)).length
}

export function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`
  return `${(bytes / 1024).toFixed(1)} KB`
}
