import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MetricStrip from './MetricStrip.vue'

describe('MetricStrip', () => {
  it('keeps unknown backend metrics distinct from zero', () => {
    const wrapper = mount(MetricStrip, {
      props: {
        items: [
          { key: 'unknown', label: '未采样', value: null },
          { key: 'zero', label: '真实为零', value: 0 },
        ],
      },
    })

    expect(wrapper.text()).toContain('未采样—')
    expect(wrapper.text()).toContain('真实为零0')
  })
})
