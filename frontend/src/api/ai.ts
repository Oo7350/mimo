const API_KEY = "sk-e1b6b4bcca40494e91663036ec113a22";
const BASE_URL = "https://api.deepseek.com/chat/completions";

async function chat(systemMessage: string, userMessage: string): Promise<string> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${API_KEY}`,
    },
    body: JSON.stringify({
      model: "deepseek-chat",
      messages: [
        { role: "system", content: systemMessage },
        { role: "user", content: userMessage },
      ],
      temperature: 0.7,
      max_tokens: 1024,
    }),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(`DeepSeek API error ${res.status}: ${err}`);
  }
  const data = await res.json();
  return data.choices?.[0]?.message?.content || "";
}

export async function polishDescription(input: string): Promise<string> {
  return chat(
    "你是一个专业的项目管理助手，擅长将粗糙的草稿润色为正式、清晰、可执行的任务描述。使用中文输出，保持原意不添加新信息。",
    `请将以下任务描述润色为更专业、清晰的表达:\n\n${input}`
  );
}

export async function analyzePriority(title: string, description: string): Promise<{ priority: string; reason: string }> {
  const text = description
    ? `标题: ${title}\n描述: ${description}`
    : `标题: ${title}`;
  const result = await chat(
    '你是一个敏捷项目管理专家，根据任务的紧急性和重要性评估优先级。只返回JSON格式，不要其他内容。',
    `根据以下任务信息，推荐最合适的优先级（HIGHEST/HIGH/MEDIUM/LOW/LOWEST），返回JSON: {"priority": "HIGH", "reason": "简短理由(中文，不超过30字)"}\n\n${text}`
  );
  try {
    // Try to extract JSON from response
    const match = result.match(/\{[\s\S]*\}/);
    if (match) return JSON.parse(match[0]);
  } catch { /* fall through */ }
  return { priority: "MEDIUM", reason: "无法解析AI响应，使用默认值" };
}
