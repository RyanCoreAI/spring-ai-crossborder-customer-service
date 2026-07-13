import { describe, expect, it } from 'vitest'
import { consumeSse } from './sse'

describe('consumeSse', () => {
  it('parses fragmented and multiline SSE events', async () => {
    const encoder = new TextEncoder()
    const chunks = [
      'event: status\ndata: PROC',
      'ESSING\n\nevent: translated_delta\ndata: 第一行\ndata: 第二行\n\n',
      'event: final\ndata: 完成\n\n',
    ]
    const stream = new ReadableStream({
      start(controller) {
        chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)))
        controller.close()
      },
    })
    const events: Array<{ event: string; data: string }> = []

    await consumeSse(new Response(stream), (event) => {
      events.push(event)
    })

    expect(events).toEqual([
      { event: 'status', data: 'PROCESSING' },
      { event: 'translated_delta', data: '第一行\n第二行' },
      { event: 'final', data: '完成' },
    ])
  })
})
