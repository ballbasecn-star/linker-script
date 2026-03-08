const state = {
  ingestCount: 0,
  generationCount: 0,
  latestScriptUuid: "",
};

const healthStatus = document.querySelector("#healthStatus");
const healthHint = document.querySelector("#healthHint");
const ingestMetric = document.querySelector("#ingestMetric");
const fragmentMetric = document.querySelector("#fragmentMetric");
const searchMetric = document.querySelector("#searchMetric");
const generationMetric = document.querySelector("#generationMetric");
const ingestLog = document.querySelector("#ingestLog");
const scriptStatusTag = document.querySelector("#scriptStatusTag");
const scriptMeta = document.querySelector("#scriptMeta");
const fragmentList = document.querySelector("#fragmentList");
const searchResults = document.querySelector("#searchResults");
const generationOutput = document.querySelector("#generationOutput");
const scriptUuidInput = document.querySelector("#scriptUuidInput");

document.querySelectorAll("[data-scroll-target]").forEach((button) => {
  button.addEventListener("click", () => {
    const target = document.querySelector(button.dataset.scrollTarget);
    if (target) {
      target.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  });
});

document.querySelector("#ingestForm").addEventListener("submit", onIngestSubmit);
document.querySelector("#loadScriptBtn").addEventListener("click", onLoadScript);
document.querySelector("#searchForm").addEventListener("submit", onSearchSubmit);
document.querySelector("#generateForm").addEventListener("submit", onGenerateSubmit);

refreshHealth();

async function refreshHealth() {
  try {
    const body = await requestJson("/actuator/health");
    healthStatus.textContent = body.status || "UNKNOWN";
    healthStatus.className = body.status === "UP" ? "status-ok" : "";
    healthHint.textContent = "后端接口已连接";
  } catch (error) {
    healthStatus.textContent = "DOWN";
    healthStatus.className = "status-failed";
    healthHint.textContent = error.message;
  }
}

async function onIngestSubmit(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const formData = new FormData(form);
  const payload = {
    title: formData.get("title"),
    content: formData.get("content"),
    sourcePlatform: formData.get("sourcePlatform"),
    externalId: formData.get("externalId"),
    statistics: {
      likes: Number(formData.get("likes") || 0),
      shares: Number(formData.get("shares") || 0),
    },
  };

  try {
    ingestLog.textContent = "素材已提交，正在创建脚本并触发异步分析...";
    const body = await requestJson("/api/v1/scripts/ingest", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    state.ingestCount += 1;
    state.latestScriptUuid = body.scriptUuid;
    ingestMetric.textContent = String(state.ingestCount);
    scriptUuidInput.value = body.scriptUuid;
    ingestLog.textContent = `脚本 ${body.scriptUuid} 已创建，当前状态 ${body.status}，正在轮询分析结果。`;
    const detail = await pollScript(body.scriptUuid);
    renderScript(detail);
    ingestLog.textContent = `脚本 ${body.scriptUuid} 分析完成，共生成 ${detail.fragments.length} 个碎片。`;
  } catch (error) {
    ingestLog.textContent = `导入失败：${error.message}`;
  }
}

async function onLoadScript() {
  const scriptUuid = scriptUuidInput.value.trim();
  if (!scriptUuid) {
    ingestLog.textContent = "请先输入 scriptUuid。";
    return;
  }

  try {
    const detail = await requestJson(`/api/v1/scripts/${scriptUuid}`);
    renderScript(detail);
  } catch (error) {
    ingestLog.textContent = `加载失败：${error.message}`;
  }
}

async function onSearchSubmit(event) {
  event.preventDefault();
  const formData = new FormData(event.currentTarget);
  const params = new URLSearchParams({
    topic: String(formData.get("topic")),
    limit: String(formData.get("limit") || 3),
  });
  const type = String(formData.get("type") || "");
  if (type) {
    params.set("type", type);
  }

  try {
    const items = await requestJson(`/api/v1/fragments/search?${params.toString()}`);
    searchMetric.textContent = String(items.length);
    renderSearchResults(items);
  } catch (error) {
    searchResults.innerHTML = `<div class="empty-state">检索失败：${escapeHtml(error.message)}</div>`;
  }
}

async function onGenerateSubmit(event) {
  event.preventDefault();
  const formData = new FormData(event.currentTarget);
  const sampleUuids = String(formData.get("sampleUuids") || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

  const payload = {
    topic: formData.get("topic"),
    sampleUuids,
    options: {
      tone: formData.get("tone"),
      length: Number(formData.get("length") || 280),
    },
  };

  try {
    generationOutput.innerHTML = "<h3>生成中</h3><p>正在组装样本并调用创作链路，请稍候。</p>";
    const body = await requestJson("/api/v1/compositions/generate", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    state.generationCount += 1;
    generationMetric.textContent = String(state.generationCount);
    renderGeneration(body);
  } catch (error) {
    generationOutput.innerHTML = `<h3>生成失败</h3><p>${escapeHtml(error.message)}</p>`;
  }
}

async function pollScript(scriptUuid) {
  const maxAttempts = 45;
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const detail = await requestJson(`/api/v1/scripts/${scriptUuid}`);
    if (detail.status === "COMPLETED" || detail.status === "FAILED") {
      return detail;
    }
    await sleep(1200);
  }
  throw new Error("等待分析结果超时");
}

function renderScript(detail) {
  state.latestScriptUuid = detail.scriptUuid;
  fragmentMetric.textContent = String(detail.fragments.length);
  scriptStatusTag.textContent = detail.status;
  scriptStatusTag.className = `panel-tag ${detail.status === "COMPLETED" ? "status-ok" : ""}`;

  scriptMeta.innerHTML = `
    <h3>${escapeHtml(detail.title || "未命名脚本")}</h3>
    <p>${escapeHtml(detail.content || "无正文")}</p>
    <div class="script-meta-grid">
      <div class="meta-block">
        <span>Script UUID</span>
        <strong>${escapeHtml(detail.scriptUuid)}</strong>
      </div>
      <div class="meta-block">
        <span>Status</span>
        <strong>${escapeHtml(detail.status)}</strong>
      </div>
      <div class="meta-block">
        <span>Source</span>
        <strong>${escapeHtml(detail.sourcePlatform || "-")} / ${escapeHtml(detail.externalId || "-")}</strong>
      </div>
      <div class="meta-block">
        <span>Statistics</span>
        <strong>${escapeHtml(JSON.stringify(detail.statistics || {}, null, 2))}</strong>
      </div>
    </div>
  `;

  if (!detail.fragments.length) {
    fragmentList.innerHTML = '<div class="empty-state">还没有碎片结果</div>';
    return;
  }

  fragmentList.innerHTML = detail.fragments
    .map(
      (fragment) => `
        <article class="fragment-item">
          <div class="fragment-head">
            <span class="fragment-type">${escapeHtml(fragment.type)}</span>
          </div>
          <div class="fragment-content">${escapeHtml(fragment.content)}</div>
          <div class="fragment-desc">${escapeHtml(fragment.logicDesc || "无逻辑描述")}</div>
        </article>
      `
    )
    .join("");
}

function renderSearchResults(items) {
  if (!items.length) {
    searchResults.innerHTML = '<div class="empty-state">没有检索到匹配结果</div>';
    return;
  }

  searchResults.innerHTML = items
    .map(
      (item) => `
        <article class="result-item">
          <div class="result-head">
            <span class="fragment-type">${escapeHtml(item.type)}</span>
            <button class="ghost-btn" data-script-uuid="${escapeHtml(item.scriptUuid)}">查看脚本</button>
          </div>
          <strong>${escapeHtml(item.title || "未命名脚本")}</strong>
          <div class="result-content">${escapeHtml(item.content)}</div>
          <div class="result-meta">
            UUID: ${escapeHtml(item.scriptUuid)}<br>
            Logic: ${escapeHtml(item.logicDesc || "无")}<br>
            Score: ${Number(item.score || 0).toFixed(4)}
          </div>
        </article>
      `
    )
    .join("");

  searchResults.querySelectorAll("[data-script-uuid]").forEach((button) => {
    button.addEventListener("click", async () => {
      scriptUuidInput.value = button.dataset.scriptUuid;
      await onLoadScript();
    });
  });
}

function renderGeneration(body) {
  generationOutput.innerHTML = `
    <h3>${escapeHtml(body.topic)}</h3>
    <div class="generation-meta">
      Log ID: ${escapeHtml(String(body.logId))}<br>
      引用样本: ${escapeHtml((body.referenceUuids || []).join(", ") || "自动选择")}
    </div>
    <div class="generation-body">${escapeHtml(body.content || "")}</div>
  `;
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    ...options,
  });

  const text = await response.text();
  let body = {};
  if (text) {
    try {
      body = JSON.parse(text);
    } catch (error) {
      throw new Error(text);
    }
  }

  if (!response.ok) {
    throw new Error(body.message || `HTTP ${response.status}`);
  }

  return body;
}

function sleep(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
