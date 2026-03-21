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
if (document.querySelector("#libraryForm")) {
  document.querySelector("#libraryForm").addEventListener("submit", onLibrarySubmit);
}

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

async function onLibrarySubmit(event) {
  event.preventDefault();
  const formData = new FormData(event.currentTarget);
  const tags = String(formData.get("tags") || "").trim();
  const heatLevel = String(formData.get("heatLevel") || "S,A").trim();
  const params = new URLSearchParams({ size: 20 });
  if (tags) params.set("tags", tags);
  if (heatLevel) params.set("heatLevel", heatLevel);

  const libraryResults = document.querySelector("#libraryResults");
  libraryResults.innerHTML = '<div class="empty-state">加载中...</div>';

  try {
    const page = await requestJson(`/api/v1/scripts?${params.toString()}`);
    const list = Array.isArray(page) ? page : (page.content || []);
    if (!list.length) {
      libraryResults.innerHTML = '<div class="empty-state">未找到匹配的素材</div>';
      return;
    }
    libraryResults.innerHTML = list.map(script => {
      const heatClass = script.heatLevel ? script.heatLevel.toLowerCase() : 'd';
      const tagsHtml = script.tags && script.tags.length ? script.tags.map(t => t.name).join(", ") : "无";
      return `
      <article class="result-item">
        <div class="result-head">
           <span class="heat-${heatClass}">🔥 Level ${escapeHtml(script.heatLevel || 'D')} (Score: ${Number(script.heatScore || 0).toFixed(2)})</span>
           <button class="ghost-btn" style="padding: 4px 10px; font-size: 13px;" onclick="javascript:document.querySelector('#scriptUuidInput').value='${escapeHtml(script.scriptUuid)}'; document.querySelector('#loadScriptBtn').click(); document.querySelector('#scriptUuidInput').scrollIntoView({ behavior: 'smooth', block: 'start' });">查看详情</button>
        </div>
        <strong>${escapeHtml(script.title || "未命名脚本")}</strong>
        <div class="result-meta" style="margin-top: 8px;">
           UUID: ${escapeHtml(script.scriptUuid)}<br>
           标签: ${tagsHtml}
        </div>
      </article>
      `;
    }).join("");
  } catch (error) {
    libraryResults.innerHTML = `<div class="empty-state">加载失败：${escapeHtml(error.message)}</div>`;
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
  const maxAttempts = 150;
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
  window.currentScriptData = detail; // For easy editing access
  fragmentMetric.textContent = String(detail.fragments.length);
  scriptStatusTag.textContent = detail.status;
  scriptStatusTag.className = `panel-tag ${detail.status === "COMPLETED" ? "status-ok" : ""}`;

  const heatLevel = detail.heatLevel || 'D';
  const heatScore = Number(detail.heatScore || 0).toFixed(2);
  const heatClass = heatLevel.toLowerCase();

  scriptMeta.innerHTML = `
    <h3>${escapeHtml(detail.title || "未命名脚本")}</h3>
    <p>${escapeHtml(detail.content || "无正文")}</p>
    <div class="script-meta-grid">
      <div class="meta-block">
        <span>Script UUID</span>
        <strong>${escapeHtml(detail.scriptUuid)}</strong>
      </div>
      <div class="meta-block">
        <span>Status / Heat</span>
        <strong>${escapeHtml(detail.status)} <br><span class="heat-${heatClass}">🔥 Level ${escapeHtml(heatLevel)} (Score: ${heatScore})</span></strong>
      </div>
      <div class="meta-block">
        <span>Source</span>
        <strong>${escapeHtml(detail.sourcePlatform || "-")} / ${escapeHtml(detail.externalId || "-")}</strong>
      </div>
      <div class="meta-block">
        <span>Tags (${detail.tags ? detail.tags.length : 0})</span>
        <div class="tag-list" id="scriptTagsList">
          ${(detail.tags || []).map(t => `<span class="tag-chip">${escapeHtml(t.name)}</span>`).join("")}
          <button class="tag-chip tag-chip-add" onclick="addTagPrompt('${escapeHtml(detail.scriptUuid)}')">+ 添加标签</button>
        </div>
      </div>
    </div>
    ${renderReview(detail.review)}
  `;

  if (!detail.fragments.length) {
    fragmentList.innerHTML = '<div class="empty-state">还没有碎片结果</div>';
    return;
  }

  fragmentList.innerHTML = detail.fragments
    .map(
      (fragment) => {
        const isWarning = fragment.confidence && fragment.confidence <= 0.5;
        const confidenceLabel = isWarning
          ? '<span class="warning-label">⚠️ AI置信度偏低</span>'
          : `<span class="warning-label" style="color:var(--muted)">置信度: ${fragment.confidence || 0}</span>`;

        return `
        <article class="fragment-item ${isWarning ? 'fragment-warning' : ''}" id="frag-${fragment.id}">
          <div class="fragment-head">
            <span class="fragment-type">${escapeHtml(fragment.type)}</span>
            <div style="display: flex; gap: 12px; align-items: center;">
                ${confidenceLabel}
                <button class="ghost-btn" style="padding: 4px 10px; font-size: 12px;" onclick="editFragment(${fragment.id})">人工校正</button>
            </div>
          </div>
          <div class="fragment-content">${escapeHtml(fragment.content)}</div>
          <div class="fragment-desc">${escapeHtml(fragment.logicDesc || "无逻辑描述")}</div>
        </article>
      `}
    )
    .join("");
}

window.addTagPrompt = async function (uuid) {
  const tagName = prompt("请输入要添加的标签名称:");
  if (!tagName) return;
  const category = prompt("请输入分类标签 (如 INDUSTRY, EMOTION, AUDIENCE, STYLE, PLATFORM) [默认: STYLE]:") || "STYLE";
  try {
    await requestJson(`/api/v1/scripts/${uuid}/tags`, {
      method: "POST",
      body: JSON.stringify({
        tags: {
          [category.trim().toUpperCase()]: [tagName.trim()]
        }
      })
    });
    document.querySelector('#loadScriptBtn').click();
  } catch (e) {
    alert("添加失败: " + e.message);
  }
}

window.editFragment = function (id) {
  const frag = window.currentScriptData.fragments.find(f => f.id === id);
  if (!frag) return;

  const container = document.querySelector("#frag-" + id);
  container.innerHTML = `
    <form class="fragment-edit-form" onsubmit="saveFragment(event, ${id})">
      <label>碎片类型: <input name="type" value="${escapeHtml(frag.type)}"></label>
      <label>文案片段: <textarea name="content" rows="4">${escapeHtml(frag.content)}</textarea></label>
      <label>逻辑描述: <textarea name="logicDesc" rows="3">${escapeHtml(frag.logicDesc || "")}</textarea></label>
      <div class="fragment-edit-actions">
        <button type="button" class="ghost-btn" style="padding: 6px 14px; font-size: 13px;" onclick="document.querySelector('#loadScriptBtn').click()">取消</button>
        <button type="submit" class="primary-btn" style="padding: 6px 14px; font-size: 13px;">保存修改</button>
      </div>
    </form>
  `;
};

window.saveFragment = async function (event, id) {
  event.preventDefault();
  const form = event.currentTarget;
  const payload = {
    type: form.querySelector('[name=type]').value.trim().toUpperCase(),
    content: form.querySelector('[name=content]').value.trim(),
    logicDesc: form.querySelector('[name=logicDesc]').value.trim()
  };

  const submitBtn = form.querySelector('button[type="submit"]');
  submitBtn.textContent = '保存中...';
  submitBtn.disabled = true;

  try {
    await requestJson(`/api/v1/fragments/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    });
    document.querySelector('#loadScriptBtn').click();
  } catch (e) {
    alert("保存失败: " + e.message);
    submitBtn.textContent = '保存修改';
    submitBtn.disabled = false;
  }
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
            <button class="ghost-btn" style="padding: 4px 10px; font-size: 13px;" data-script-uuid="${escapeHtml(item.scriptUuid)}">查看来源脚本</button>
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
      scriptUuidInput.scrollIntoView({ behavior: "smooth", block: "start" });
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

function renderReview(review) {
  if (!review) {
    return `
      <section class="review-card">
        <div class="review-head">
          <h4>精选候选点评</h4>
          <span class="review-badge pending">等待分析</span>
        </div>
        <div class="empty-state">当前脚本还没有点评结果</div>
      </section>
    `;
  }

  const featuredClass = review.featuredCandidate ? "featured" : "not-featured";
  const riskFlags = Array.isArray(review.riskFlags) ? review.riskFlags : [];

  return `
    <section class="review-card">
      <div class="review-head">
        <div>
          <h4>精选候选点评</h4>
          <p>${escapeHtml(review.summary || "暂无摘要")}</p>
        </div>
        <div class="review-score-wrap">
          <span class="review-badge ${featuredClass}">${review.featuredCandidate ? "精选候选" : "待优化"}</span>
          <strong>${Number(review.overallScore || 0)}</strong>
        </div>
      </div>
      <div class="review-meta">
        <div class="meta-block">
          <span>结论</span>
          <strong>${escapeHtml(review.featuredConclusion || "-")}</strong>
        </div>
        <div class="meta-block">
          <span>原因</span>
          <strong>${escapeHtml(review.featuredReason || "-")}</strong>
        </div>
      </div>
      <div class="review-score-grid">
        ${renderReviewMetric("完整度", review.completenessScore)}
        ${renderReviewMetric("获得感", review.gainScore)}
        ${renderReviewMetric("惊喜感", review.surpriseScore)}
        ${renderReviewMetric("真实性", review.authenticityScore)}
        ${renderReviewMetric("专业度", review.professionalismScore)}
        ${renderReviewMetric("可信度", review.credibilityScore)}
        ${renderReviewMetric("有趣度", review.interestingnessScore)}
      </div>
      <div class="review-columns">
        ${renderReviewList("亮点", review.highlights)}
        ${renderReviewList("问题", review.issues)}
        ${renderReviewList("建议", review.suggestions)}
      </div>
      <div class="review-risk-row">
        <span>风险项</span>
        <div class="tag-list">
          ${riskFlags.length
            ? riskFlags.map(flag => `<span class="tag-chip tag-chip-risk">${escapeHtml(flag)}</span>`).join("")
            : '<span class="tag-chip">未发现明显风险</span>'}
        </div>
      </div>
    </section>
  `;
}

function renderReviewMetric(label, value) {
  const score = Number(value || 0);
  return `
    <article class="review-metric">
      <span>${escapeHtml(label)}</span>
      <strong>${score}</strong>
      <div class="review-meter"><i style="width:${Math.max(0, Math.min(100, score))}%"></i></div>
    </article>
  `;
}

function renderReviewList(title, items) {
  const list = Array.isArray(items) ? items.filter(Boolean) : [];
  return `
    <section class="review-list-block">
      <h5>${escapeHtml(title)}</h5>
      ${list.length
        ? `<ul>${list.map(item => `<li>${escapeHtml(item)}</li>`).join("")}</ul>`
        : '<div class="empty-inline">暂无</div>'}
    </section>
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
