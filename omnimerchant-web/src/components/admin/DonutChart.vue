<template>
  <div class="donut-chart">
    <div v-if="!normalizedData.length" class="donut-chart__empty">
      <a-empty :description="emptyText" />
    </div>
    <div
      v-else
      ref="chartElement"
      class="donut-chart__canvas"
      role="img"
      :aria-label="ariaLabel"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { use } from "echarts/core";
import { PieChart } from "echarts/charts";
import { LegendComponent, TitleComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import { init, type ECharts, type EChartsCoreOption } from "echarts/core";

use([PieChart, LegendComponent, TitleComponent, TooltipComponent, CanvasRenderer]);

type DonutDatum = { name: string; value: number };

const props = withDefaults(
  defineProps<{
    data: DonutDatum[];
    ariaLabel: string;
    emptyText?: string;
  }>(),
  { emptyText: "暂无可统计数据" },
);

const chartElement = ref<HTMLElement>();
const normalizedData = computed(() =>
  (props.data || []).filter(
    (item) => item.name && Number.isFinite(item.value) && item.value > 0,
  ),
);
let chart: ECharts | null = null;
let observer: ResizeObserver | null = null;

function option(): EChartsCoreOption {
  const compact = (chartElement.value?.clientWidth || 0) < 440;
  const total = normalizedData.value.reduce((sum, item) => sum + item.value, 0);
  const center = compact ? ["50%", "42%"] : ["31%", "50%"];
  return {
    color: ["#1677ff", "#12b76a", "#f79009", "#7a5af8", "#06aed4", "#d92d20"],
    animationDuration: 350,
    tooltip: {
      trigger: "item",
      formatter: "{b}<br/>{c} 条 · {d}%",
    },
    title: {
      text: String(total),
      subtext: "总计",
      left: center[0],
      top: center[1],
      textAlign: "center",
      textVerticalAlign: "middle",
      itemGap: 2,
      textStyle: { color: "#101828", fontSize: 22, fontWeight: 700 },
      subtextStyle: { color: "#98a2b3", fontSize: 11 },
    },
    legend: compact
      ? {
          type: "scroll",
          orient: "horizontal",
          left: 0,
          right: 0,
          bottom: 0,
          itemWidth: 8,
          itemHeight: 8,
          textStyle: { color: "#475467", fontSize: 11 },
        }
      : {
          type: "scroll",
          orient: "vertical",
          right: 4,
          top: "middle",
          width: "42%",
          itemWidth: 8,
          itemHeight: 8,
          textStyle: { color: "#475467", fontSize: 11 },
        },
    series: [
      {
        name: props.ariaLabel,
        type: "pie",
        radius: compact ? ["48%", "68%"] : ["52%", "72%"],
        center,
        avoidLabelOverlap: true,
        itemStyle: { borderColor: "#fff", borderWidth: 2 },
        label: { show: false },
        emphasis: {
          scaleSize: 4,
          label: { show: true, formatter: "{d}%", fontSize: 15, fontWeight: 700 },
        },
        data: normalizedData.value,
      },
    ],
  };
}

function render() {
  if (!chartElement.value || !normalizedData.value.length) {
    chart?.dispose();
    chart = null;
    return;
  }
  if (!chart) chart = init(chartElement.value, undefined, { renderer: "canvas" });
  chart.setOption(option(), true);
}

onMounted(async () => {
  await nextTick();
  render();
  observer = new ResizeObserver(() => {
    chart?.resize();
    if (chart) chart.setOption(option(), true);
  });
  if (chartElement.value) observer.observe(chartElement.value);
});

watch(normalizedData, async () => {
  await nextTick();
  render();
}, { deep: true });

onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>

<style scoped>
.donut-chart,
.donut-chart__canvas,
.donut-chart__empty {
  width: 100%;
  height: 236px;
}
.donut-chart__empty {
  display: grid;
  place-items: center;
}
@media (max-width: 560px) {
  .donut-chart,
  .donut-chart__canvas,
  .donut-chart__empty {
    height: 270px;
  }
}
</style>
