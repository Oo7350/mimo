/**
 * ECharts 按需注册入口
 *
 * 之前各组件都写 `import * as echarts from 'echarts'`，会把 ECharts 全量
 * （地图 / 雷达 / 树图 / 桑基 / 热力图 等所有图表类型和无用组件）打进产物，
 * 共享 chunk 约 1.03 MB。这里只注册项目实际用到的部分，业务代码统一从本模块导入：
 *
 *   import * as echarts from "@/utils/echarts";
 *
 * 图表类型：line / bar / pie / custom（甘特图）/ graph（甘特图依赖箭头）
 * 组件：grid / tooltip / legend / title / markLine / axisPointer / visualMap
 * 渲染器：Canvas
 *
 * 新增图表类型（如 scatter、radar）时需要在上面的 use() 里补充注册，
 * 否则运行时会报错「Series xxx is not exists. Load it first.」。
 */
import * as echarts from "echarts/core";
import { BarChart, CustomChart, GraphChart, LineChart, PieChart } from "echarts/charts";
import {
  AxisPointerComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
  VisualMapComponent,
} from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  CustomChart,
  GraphChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  MarkLineComponent,
  AxisPointerComponent,
  VisualMapComponent,
  CanvasRenderer,
]);

// 透传 echarts/core 的全部导出（init / graphic / ECharts 类型等），
// 这样业务侧 `echarts.init(...)`、`echarts.graphic.LinearGradient`、`echarts.ECharts` 都无需改动。
export * from "echarts/core";
