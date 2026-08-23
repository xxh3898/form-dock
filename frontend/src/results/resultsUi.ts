import type { CsvDownload } from './resultsClient.ts'

export function parsePositiveRouteId(value: string | undefined): number | null {
  if (value === undefined || !/^[1-9][0-9]*$/.test(value)) {
    return null
  }
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) ? parsed : null
}

export function formatResultTimestamp(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function saveCsvDownload(download: CsvDownload): void {
  const objectUrl = URL.createObjectURL(download.blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = download.filename
  link.hidden = true
  document.body.append(link)
  try {
    link.click()
  } finally {
    link.remove()
    URL.revokeObjectURL(objectUrl)
  }
}
