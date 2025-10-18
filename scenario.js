(function(){
  'use strict';

  // Elements
  const hud = document.getElementById('scenarioHud');
  const overlay = document.getElementById('scnOverlay');
  const highlight = document.getElementById('scnHighlight');
  const hintImg = document.getElementById('scnHintImg');
  const tooltip = document.getElementById('scnTooltip');

  const btnRecord = document.getElementById('scnRecord');
  const btnStop = document.getElementById('scnStop');
  const btnPlay = document.getElementById('scnPlay');
  const btnStep = document.getElementById('scnStep');
  const chkAutopilot = document.getElementById('scnAutopilot');
  const btnExport = document.getElementById('scnExport');
  const btnImport = document.getElementById('scnImport');
  const fileImport = document.getElementById('scnImportFile');
  const btnClear = document.getElementById('scnClear');
  const btnAttachHint = document.getElementById('scnAttachHint');
  const fileHint = document.getElementById('scnHintFile');
  const stepsList = document.getElementById('scnSteps');
  const scnCount = document.getElementById('scnCount');
  const details = document.getElementById('scnDetails');

  if(!hud) return;

  // Scenario model
  /**
   * Step shape:
   * {
   *   id: string,
   *   type: 'tap',
   *   ts: number,
   *   selector: string,          // CSS selector fingerprint
   *   rect: {x,y,w,h},           // bounding box at record time
   *   hintImage?: string,        // data URL (image) attached to step
   *   note?: string
   * }
   */
  const STORAGE_KEY = 'demo.scenario.v1';
  let scenario = loadScenario();
  let recording = false;
  let running = false;
  let currentIndex = 0;

  function saveScenario(){
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(scenario)); } catch {}
  }
  function loadScenario(){
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : { steps: [] };
    } catch {
      return { steps: [] };
    }
  }

  function updateCount(){
    scnCount.textContent = scenario.steps.length + ' 단계';
    renderSteps();
  }

  function renderSteps(){
    stepsList.innerHTML = '';
    scenario.steps.forEach((s, idx) => {
      const li = document.createElement('li');
      li.textContent = `${idx+1}. ${s.type} ${s.selector || '(요소 없음)'}${s.note? ' - '+s.note: ''}`;
      stepsList.appendChild(li);
    });
  }

  function elementFingerprint(el){
    if(!el || el === document.body || el === document.documentElement) return 'body';
    const parts = [];
    if(el.id) parts.push('#' + CSS.escape(el.id));
    if(el.classList.length) parts.push('.' + [...el.classList].map(c => CSS.escape(c)).join('.'));
    const tag = el.tagName?.toLowerCase();
    if(tag) parts.unshift(tag);

    let selector = parts.join('');

    // Disambiguate with nth-child if necessary
    try {
      if(selector){
        const matches = document.querySelectorAll(selector);
        if(matches.length > 1){
          const parent = el.parentElement;
          if(parent){
            const index = [...parent.children].indexOf(el) + 1;
            selector = `${selector}:nth-child(${index})`;
          }
        }
      }
    } catch {}

    return selector || 'body';
  }

  function rectOf(el){
    try{
      const r = el.getBoundingClientRect();
      return { x: r.left + window.scrollX, y: r.top + window.scrollY, w: r.width, h: r.height };
    }catch{ return { x:0, y:0, w:0, h:0 }; }
  }

  function highlightRect(rect){
    if(!rect){ overlay.hidden = true; return; }
    overlay.hidden = false;
    highlight.style.left = rect.x + 'px';
    highlight.style.top = rect.y + 'px';
    highlight.style.width = rect.w + 'px';
    highlight.style.height = rect.h + 'px';
  }

  function showTooltip(text, rect){
    if(!text || !rect){ tooltip.hidden = true; return; }
    tooltip.textContent = text;
    const pad = 8;
    tooltip.style.left = rect.x + 'px';
    tooltip.style.top = (rect.y - 28 - pad) + 'px';
    tooltip.hidden = false;
  }

  function hideOverlay(){
    overlay.hidden = true;
    tooltip.hidden = true;
    hintImg.hidden = true;
  }

  function recordTap(target){
    if(!recording) return;
    // ignore HUD elements
    const root = target?.closest?.('[data-scenario-ignore]');
    if(root) return;

    const selector = elementFingerprint(target);
    const step = {
      id: Math.random().toString(36).slice(2,9),
      type: 'tap',
      ts: Date.now(),
      selector,
      rect: rectOf(target)
    };
    scenario.steps.push(step);
    saveScenario();
    updateCount();
    flashHUD('기록: ' + (target?.ariaLabel || target?.innerText || target?.tagName));
  }

  function queryByStep(step){
    try{ return document.querySelector(step.selector); } catch { return null; }
  }

  function flashHUD(text){
    scnCount.textContent = text;
    setTimeout(updateCount, 800);
  }

  function playStep(step){
    const el = queryByStep(step);
    if(!el){
      // try fallback by rect
      highlightRect(step.rect);
      showTooltip('요소를 찾을 수 없습니다. 위치를 탭하세요.', step.rect);
      return false;
    }

    const r = rectOf(el);
    highlightRect(r);
    showTooltip('여기를 탭', r);

    // Auto click on autopilot
    if(chkAutopilot.checked){
      try { el.click(); } catch {}
    }
    return true;
  }

  function stepNext(){
    if(currentIndex >= scenario.steps.length){ stopRun(); return; }
    const ok = playStep(scenario.steps[currentIndex]);
    if(!ok){
      // wait user to click near rect
      const waitOnce = (ev) => {
        const pt = { x: ev.clientX + window.scrollX, y: ev.clientY + window.scrollY };
        const r = scenario.steps[currentIndex].rect;
        const inside = pt.x >= r.x && pt.x <= r.x + r.w && pt.y >= r.y && pt.y <= r.y + r.h;
        if(inside){
          window.removeEventListener('click', waitOnce, true);
          currentIndex += 1; stepNext();
        }
      };
      window.addEventListener('click', waitOnce, true);
      return;
    }

    if(chkAutopilot.checked){
      setTimeout(() => { currentIndex += 1; stepNext(); }, 600);
    }
  }

  function startRun(){
    if(running) return;
    if(scenario.steps.length === 0){ flashHUD('시나리오가 없습니다'); return; }
    running = true; currentIndex = 0;
    overlay.hidden = false; details.open = true;
    stepNext();
  }
  function stopRun(){ running = false; hideOverlay(); }

  // Image hints attach to the last step
  function attachHintToLast(file){
    if(!file) return;
    const last = scenario.steps[scenario.steps.length-1];
    if(!last){ flashHUD('단계가 없습니다'); return; }
    const reader = new FileReader();
    reader.onload = () => {
      last.hintImage = String(reader.result);
      saveScenario();
      updateCount();
      flashHUD('힌트 이미지 첨부됨');
    };
    reader.readAsDataURL(file);
  }

  function exportScenario(){
    const blob = new Blob([JSON.stringify(scenario, null, 2)], {type:'application/json'});
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'scenario.json';
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }

  function importScenario(file){
    if(!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const data = JSON.parse(String(reader.result));
        if(data && Array.isArray(data.steps)){
          scenario = { steps: data.steps };
          saveScenario(); updateCount(); flashHUD('시나리오 가져오기 완료');
        } else { flashHUD('형식 오류'); }
      } catch { flashHUD('가져오기 실패'); }
    };
    reader.readAsText(file);
  }

  // HUD events
  btnRecord.addEventListener('click', () => {
    recording = !recording;
    btnRecord.setAttribute('aria-pressed', String(recording));
    btnRecord.textContent = recording ? '기록 중' : '기록';
    if(recording){ flashHUD('기록 시작'); }
  });
  btnStop.addEventListener('click', () => {
    recording = false; btnRecord.setAttribute('aria-pressed', 'false'); btnRecord.textContent = '기록';
    stopRun(); flashHUD('정지');
  });
  btnPlay.addEventListener('click', startRun);
  btnStep.addEventListener('click', () => { chkAutopilot.checked = false; if(!running){ running = true; overlay.hidden = false; } stepNext(); });
  btnExport.addEventListener('click', exportScenario);
  btnImport.addEventListener('click', () => fileImport.click());
  fileImport.addEventListener('change', () => importScenario(fileImport.files?.[0]));
  btnClear.addEventListener('click', () => { scenario = { steps: [] }; saveScenario(); updateCount(); flashHUD('초기화 완료'); });
  btnAttachHint.addEventListener('click', () => fileHint.click());
  fileHint.addEventListener('change', () => attachHintToLast(fileHint.files?.[0]));

  // Global capture for recording taps
  window.addEventListener('click', (ev) => {
    recordTap(ev.target);
  }, true);

  // When playing, display hint image for the current step if present
  const observer = new MutationObserver(() => {
    if(!running) return;
    const s = scenario.steps[currentIndex];
    if(!s){ hideOverlay(); return; }
    if(s.hintImage){
      hintImg.src = s.hintImage; hintImg.hidden = false;
      // place the hint image near highlight (right side)
      const r = s.rect; const pad = 12;
      hintImg.style.left = (r.x + r.w + pad) + 'px';
      hintImg.style.top = Math.max(12, r.y - 8) + 'px';
    } else { hintImg.hidden = true; }
  });
  observer.observe(document.body, { attributes:true, childList:true, subtree:true });

  // Init
  updateCount();
})();
