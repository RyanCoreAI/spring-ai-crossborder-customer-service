import { describe, expect, it } from 'vitest'
import { failureCategoryLabel, intentLabel, rerankerModeLabel, statusLabel, toolsLabel } from './display'

describe('business display labels', () => {
  it('renders known backend enum values in Chinese', () => {
    expect(intentLabel('RETURN_REFUND')).toBe('退货退款')
    expect(failureCategoryLabel('RAG_NO_CITATION')).toBe('缺少引用')
    expect(rerankerModeLabel('fallback-rrf')).toContain('RRF')
    expect(statusLabel('WAITING_CREDENTIALS')).toBe('等待凭据')
  })

  it('does not invent values for unknown backend fields', () => {
    expect(intentLabel('CUSTOM_NEW_INTENT')).toBe('CUSTOM_NEW_INTENT')
    expect(toolsLabel('["queryOrder","trackLogistics"]')).toContain('查询订单')
  })
})
