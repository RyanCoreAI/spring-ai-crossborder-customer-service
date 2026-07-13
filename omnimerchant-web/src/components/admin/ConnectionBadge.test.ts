import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ConnectionBadge from './ConnectionBadge.vue'

describe('ConnectionBadge', () => {
  it.each([
    ['LIVE', '真实连接'],
    ['FIXTURE', 'Fixture 演示'],
    ['WAITING_CREDENTIALS', '等待凭据'],
    ['DEGRADED', '降级'],
  ])('renders backend connection state %s', (status, label) => {
    const wrapper = mount(ConnectionBadge, { props: { status } })
    expect(wrapper.text()).toBe(label)
  })
})
