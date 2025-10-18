(function(){
  'use strict';

  const html = document.documentElement;
  const orientationLabel = document.getElementById('orientationLabel');
  const counterValueEl = document.getElementById('counterValue');
  const actionBtn = document.getElementById('actionBtn');
  const resetBtn = document.getElementById('resetBtn');
  const stickyStatus = document.getElementById('stickyStatus');
  const speedStatus = document.getElementById('speedStatus');
  const hintOverlay = document.getElementById('hintOverlay');
  const hintDismiss = document.getElementById('hintDismiss');

  // State
  let counter = 0;
  let holding = false;         // whether user is holding currently
  let stickyRepeat = false;    // double-tap toggles sticky repeat mode
  let repeatTimer = null;      // timeout id for recursive repeat
  let holdThresholdTimer = null; // to decide if it's a long-press
  let lastTapTime = 0;         // for double-tap detection
  let pointerDownTime = 0;     // to compute press duration

  const HOLD_THRESHOLD_MS = 280; // long press threshold
  const INITIAL_INTERVAL_MS = 380; // start interval for repeat
  const MIN_INTERVAL_MS = 80;     // min interval during acceleration
  const ACCEL_STEP_MS = 30;       // acceleration step
  const ACCEL_EVERY_MS = 700;     // how often we accelerate

  function safeVibrate(pattern){
    try { navigator.vibrate?.(pattern); } catch { /* ignore */ }
  }

  function formatSpeedLabel(intervalMs){
    if(!intervalMs) return '정지';
    const perSec = (1000/intervalMs).toFixed(1);
    return perSec + '/s';
  }

  function isPortrait(){
    // Prefer media query; fallback to aspect
    const mq = window.matchMedia?.('(orientation: portrait)');
    if(mq) return mq.matches;
    return window.innerHeight >= window.innerWidth;
  }

  function getOrientationText(){
    return isPortrait() ? '세로 모드' : '가로 모드';
  }

  function applyOrientation(){
    const portrait = isPortrait();
    html.classList.toggle('is-portrait', portrait);
    html.classList.toggle('is-landscape', !portrait);
    if(orientationLabel) orientationLabel.textContent = getOrientationText();
  }

  function updateRingProgress(progress){
    // progress 0..1 visualized via CSS conic-gradient
    const clamped = Math.max(0, Math.min(1, progress || 0));
    html.style.setProperty('--press-progress', String(clamped));
  }

  function performAction(){
    counter += 1;
    counterValueEl.textContent = String(counter);
  }

  function setSpeedLabel(interval){
    speedStatus.textContent = formatSpeedLabel(interval);
  }

  function setStickyLabel(){
    stickyStatus.textContent = stickyRepeat ? '켜기' : '끄기';
    actionBtn.setAttribute('aria-pressed', String(stickyRepeat));
  }

  function stopRepeat(){
    holding = false;
    if(repeatTimer){ clearTimeout(repeatTimer); repeatTimer = null; }
    setSpeedLabel(0);
    updateRingProgress(0);
  }

  function repeatWithAcceleration(currentInterval){
    performAction();
    setSpeedLabel(currentInterval);

    let nextInterval = Math.max(MIN_INTERVAL_MS, currentInterval - ACCEL_STEP_MS);

    repeatTimer = setTimeout(() => {
      // keep repeating if holding or sticky mode
      if(holding || stickyRepeat){
        repeatWithAcceleration(nextInterval);
      } else {
        stopRepeat();
      }
    }, currentInterval);
  }

  function startRepeat(){
    if(repeatTimer) return; // already repeating
    holding = true;
    safeVibrate(10);
    repeatWithAcceleration(INITIAL_INTERVAL_MS);
  }

  // Double-tap detection: within 300ms
  function handleTap(){
    const now = Date.now();
    if(now - lastTapTime < 300){
      // double tap: toggle sticky repeat mode
      stickyRepeat = !stickyRepeat;
      setStickyLabel();
      if(stickyRepeat && !repeatTimer){
        startRepeat();
      }
      if(!stickyRepeat && !holding){
        stopRepeat();
      }
      safeVibrate([5, 20, 5]);
      lastTapTime = 0;
    } else {
      // single tap: one action
      performAction();
      lastTapTime = now;
    }
  }

  function createRipple(e){
    const rect = actionBtn.getBoundingClientRect();
    const x = (e.clientX ?? (rect.left + rect.width/2)) - rect.left;
    const y = (e.clientY ?? (rect.top + rect.height/2)) - rect.top;
    const maxDim = Math.max(rect.width, rect.height);
    const ripple = document.createElement('span');
    ripple.className = 'ripple';
    ripple.style.left = x + 'px';
    ripple.style.top = y + 'px';
    ripple.style.width = ripple.style.height = String(maxDim) + 'px';
    actionBtn.appendChild(ripple);
    ripple.addEventListener('animationend', () => ripple.remove(), { once: true });
  }

  function onPointerDown(e){
    if(e.pointerType === 'mouse' && e.button !== 0) return; // left only
    pointerDownTime = Date.now();
    createRipple(e);

    // show hold ring progress up to threshold
    const startTime = Date.now();
    function tick(){
      const elapsed = Date.now() - startTime;
      const progress = Math.min(1, elapsed / HOLD_THRESHOLD_MS);
      updateRingProgress(progress);
      if(holding) return; // once we start repeat, stop the pre-ring animation
      if(progress < 1){ requestAnimationFrame(tick); }
    }
    requestAnimationFrame(tick);

    // decide long press after threshold
    holdThresholdTimer = setTimeout(() => {
      startRepeat();
    }, HOLD_THRESHOLD_MS);

    // Prevent context menu on long-press (mobile)
    actionBtn.addEventListener('contextmenu', preventOnce, { once: true });
  }

  function preventOnce(ev){ ev.preventDefault(); }

  function onPointerUp(){
    const pressDuration = Date.now() - pointerDownTime;
    clearTimeout(holdThresholdTimer); holdThresholdTimer = null;

    if(holding){
      // already in long-press repeat: stop unless sticky
      holding = false;
      if(!stickyRepeat){ stopRepeat(); }
    } else {
      // short press: treat as tap (single/double handled)
      handleTap();
      updateRingProgress(0);
    }
  }

  function onPointerCancel(){
    clearTimeout(holdThresholdTimer); holdThresholdTimer = null;
    holding = false;
    if(!stickyRepeat){ stopRepeat(); }
  }

  function onKeyDown(e){
    if(e.code === 'Space' || e.code === 'Enter'){
      e.preventDefault();
      if(e.repeat) return; // ignore key auto-repeat; we control repeat
      pointerDownTime = Date.now();
      holdThresholdTimer = setTimeout(() => startRepeat(), HOLD_THRESHOLD_MS);
    }
  }
  function onKeyUp(e){
    if(e.code === 'Space' || e.code === 'Enter'){
      e.preventDefault();
      clearTimeout(holdThresholdTimer); holdThresholdTimer = null;
      if(holding){ holding = false; if(!stickyRepeat){ stopRepeat(); } }
      else { handleTap(); }
    }
  }

  function reset(){
    counter = 0; counterValueEl.textContent = '0';
    stickyRepeat = false; setStickyLabel(); stopRepeat();
  }

  function maybeShowHints(){
    try{
      const key = 'demo.hints.dismissed.v1';
      if(localStorage.getItem(key) !== '1'){
        hintOverlay.hidden = false;
        hintDismiss.addEventListener('click', () => {
          hintOverlay.hidden = true;
          localStorage.setItem(key, '1');
        }, { once: true });
      }
    }catch{ /* ignore private mode errors */ }
  }

  // Orientation listeners
  applyOrientation();
  window.addEventListener('resize', applyOrientation);
  window.addEventListener('orientationchange', applyOrientation);
  try { screen.orientation?.addEventListener?.('change', applyOrientation); } catch { /* no-op */ }

  // Button listeners
  actionBtn.addEventListener('pointerdown', onPointerDown);
  window.addEventListener('pointerup', onPointerUp);
  window.addEventListener('pointercancel', onPointerCancel);
  actionBtn.addEventListener('keydown', onKeyDown);
  actionBtn.addEventListener('keyup', onKeyUp);

  resetBtn.addEventListener('click', reset);

  // Init
  setStickyLabel();
  setSpeedLabel(0);
  maybeShowHints();

  // Remove no-js class
  html.classList.remove('no-js');
})();
