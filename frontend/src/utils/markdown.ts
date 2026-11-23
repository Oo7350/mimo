import { marked } from "marked";
import DOMPurify from "dompurify";

marked.setOptions({
  breaks: true,
  gfm: true,
});

/**
 * 把一段 HTML 洗成安全的形态（Markdown 渲染结果、邮件正文都走这里）。
 *
 * 这里以前是**正则黑名单**（删 `<script>`、`on\w+="…"`、`javascript:` 之类）。
 * 正则做不了 HTML 解析，`e2e/xss-markdown.mjs` 用真实浏览器验证过它挡不住：
 *
 * | 载荷 | 结果 |
 * |---|---|
 * | `<details open ontoggle=…>` | 事件处理器不带引号就不匹配正则，**直接执行** |
 * | `[点我](jav&#x61;script:…)` | 实体解码后得到 `javascript:` 链接，点一下就执行 |
 * | `<math><mtext><table><mglyph><style>…` | mXSS 解析器混淆，`<style>` 留在 DOM 里 |
 *
 * 换成 DOMPurify：按真实 DOM 解析再按白名单重建，上面三类都被挡掉，
 * 同时正常 Markdown（粗体/代码/列表/链接）渲染不受影响。
 * 改这个函数前后都跑一遍 `node e2e/xss-markdown.mjs`（10 条载荷，全绿才算数）。
 */
export function sanitizeHtml(html: string): string {
  if (!html) return "";
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } });
}

export function renderMarkdown(text: string): string {
  if (!text) return "";
  return sanitizeHtml(marked.parse(text) as string);
}
