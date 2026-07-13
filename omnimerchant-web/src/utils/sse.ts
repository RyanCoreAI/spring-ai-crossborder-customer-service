export interface SseMessage {
  event: string
  data: string
}

export async function consumeSse(response: Response, onMessage: (message: SseMessage) => void | Promise<void>) {
  const reader = response.body?.getReader()
  if (!reader) throw new Error('响应流为空')

  const decoder = new TextDecoder()
  let buffer = ''
  let completed = false
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        await emitBlock(block, onMessage)
        boundary = buffer.indexOf('\n\n')
      }
    }
    buffer += decoder.decode()
    if (buffer.trim()) await emitBlock(buffer, onMessage)
    completed = true
  } finally {
    if (!completed) await reader.cancel().catch(() => undefined)
  }
}

async function emitBlock(block: string, onMessage: (message: SseMessage) => void | Promise<void>) {
  let event = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  if (data.length > 0) await onMessage({ event, data: data.join('\n') })
}
