import { marked } from "marked";

// Safe renderer: allow basic formatting, strip dangerous tags
marked.setOptions({
  breaks: true,
  gfm: true,
});

// Strip script/iframe/object tags from output
function sanitize(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<iframe[\s\S]*?<\/iframe>/gi, "")
    .replace(/<object[\s\S]*?<\/object>/gi, "")
    .replace(/on\w+="[^"]*"/gi, "")
    .replace(/on\w+='[^']*'/gi, "");
}

export function renderMarkdown(text: string): string {
  if (!text) return "";
  return sanitize(marked.parse(text) as string);
}
