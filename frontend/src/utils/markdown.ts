import { marked } from "marked";

// Safe renderer: allow basic formatting, strip dangerous tags
marked.setOptions({
  breaks: true,
  gfm: true,
});

// Strip dangerous tags/attributes from HTML output
function sanitize(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<iframe[\s\S]*?<\/iframe>/gi, "")
    .replace(/<object[\s\S]*?<\/object>/gi, "")
    .replace(/<embed[^>]*>/gi, "")
    .replace(/on\w+\s*=\s*"[^"]*"/gi, "")
    .replace(/on\w+\s*=\s*'[^']*'/gi, "")
    .replace(/javascript:/gi, "")
    .replace(/data:\s*text\/html/gi, "");
}

export function renderMarkdown(text: string): string {
  if (!text) return "";
  return sanitize(marked.parse(text) as string);
}
