package com.pos.sunmiprinter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * DisplayHttpServer — 客顯 HTTP Server
 * 版本：v20260602
 *
 * 職責：
 *   - 監聽 0.0.0.0:8081（區域網路可存取，iPad 可直接連線）
 *   - 接收 Web POS 傳來的客顯資料（購物車、付款完成、待機畫面）
 *   - 保存最新狀態到 DisplayStateManager，供客顯頁面 polling 取得
 *   - 直接提供客顯 HTML 頁面（解決 HTTPS Mixed Content 問題）
 *
 * v20260602 變更：
 *   - 修正 HTML 內全形 Unicode escape 亂碼（&#８２５３; 等）改為正確半形碼點
 *   - 版面改為「左半邊購物車 + 合計金額置於左下、右半邊商品圖輪播」
 *   - 讀取 payload 的 slides（圖片 URL 陣列）做右半邊輪播；無圖時右半邊隱藏、購物車佔滿
 *
 * API 端點：
 *   GET  /display/        — 客顯 HTML 頁面（iPad 直接開啟此網址）
 *   POST /display/update  — Web POS 推送最新客顯資料（需 X-API-Token）
 *   GET  /display/state   — 客顯頁面輪詢取得最新狀態（無需 Token）
 *   GET  /display/ping    — 心跳（無需 Token）
 *   OPTIONS *             — CORS preflight
 *
 * iPad 使用方式：
 *   開啟 http://[Sunmi T2 區域 IP]:8081/display/
 *   IP 顯示在 APK 健康檢查頁首頁
 */
public class DisplayHttpServer extends NanoHTTPD {

    private static final String TAG = "DisplayHttpServer";
    public static final int DEFAULT_PORT = 8081;

    private final AppSettings settings;

    public DisplayHttpServer(int port) {
        // ★ 改為 0.0.0.0 讓區域網路的 iPad 也能連入
        super("0.0.0.0", port);
        this.settings = LogManager.getAppSettings();
        LogManager.i(TAG, "DisplayHttpServer created on 0.0.0.0:" + port);
    }

    // ==================== 路由 ====================

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();

        // CORS preflight
        if (Method.OPTIONS.equals(method)) {
            return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", ""));
        }

        try {
            // ── 無需 Token 的端點 ──

            // 客顯 HTML 頁面（iPad 直接開啟）
            if (Method.GET.equals(method) &&
                    ("/display/".equals(uri) || "/display".equals(uri))) {
                return handleDisplayPage();
            }

            // 心跳
            if (Method.GET.equals(method) && "/display/ping".equals(uri)) {
                return handlePing();
            }

            // 客顯頁面 Polling（不需 Token，避免跨域 Token 問題）
            if (Method.GET.equals(method) && "/display/state".equals(uri)) {
                return handleGetState();
            }

            // ── 需要 Token 的端點 ──
            if (!checkToken(session)) {
                LogManager.w(TAG, "display unauthorized: " + method + " " + uri);
                return cors(json(false, null, "unauthorized"));
            }

            if (Method.POST.equals(method) && "/display/update".equals(uri)) {
                return handleUpdate(session);
            }

            LogManager.w(TAG, "display not found: " + method + " " + uri);
            return cors(json(false, null, "not found: " + uri));

        } catch (Throwable t) {
            LogManager.e(TAG, "display serve error: " + uri, t);
            return cors(json(false, null, "server error: " + t.getMessage()));
        }
    }

    // ==================== Handler：客顯 HTML 頁面 ====================

    private Response handleDisplayPage() {
        LogManager.d(TAG, "handleDisplayPage: serving customer display HTML");
        String html = buildDisplayHtml();
        Response r = newFixedLengthResponse(
                Response.Status.OK, "text/html; charset=utf-8", html);
        r.addHeader("Cache-Control", "no-cache");
        return cors(r);
    }

    // ==================== 產生客顯 HTML ====================

    private String buildDisplayHtml() {
        // 取得目前店家名稱（從 APK 設定取，若無則用預設值）
        String storeName = "餐廳 POS";
        try {
            if (settings != null) {
                String sn = settings.getStoreName();
                if (sn != null && !sn.trim().isEmpty()) storeName = sn.trim();
            }
        } catch (Throwable ignored) {}

        return "<!doctype html>\n" +
"<html lang=\"zh-Hant\">\n" +
"<head>\n" +
"<meta charset=\"UTF-8\">\n" +
"<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
"<title>客顯</title>\n" +
"<style>\n" +
"*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}\n" +
":root{\n" +
"  --bg-idle:#0f172a;--bg-cart:#f8fafc;--bg-paid:#064e3b;\n" +
"  --primary:#2563eb;--ok:#10b981;\n" +
"  --text-dark:#111827;--text-light:#f1f5f9;\n" +
"  --muted:#94a3b8;--line:#e2e8f0;\n" +
"  --font:-apple-system,BlinkMacSystemFont,'PingFang TC','Noto Sans TC',sans-serif;\n" +
"}\n" +
"html,body{width:100%;height:100%;font-family:var(--font);overflow:hidden;\n" +
"  background:var(--bg-idle);color:var(--text-light);}\n" +
".screen{position:fixed;inset:0;display:flex;flex-direction:column;\n" +
"  align-items:center;justify-content:center;\n" +
"  transition:opacity 0.4s ease;opacity:0;pointer-events:none;}\n" +
".screen.active{opacity:1;pointer-events:auto;}\n" +
"\n" +
"/* 待機畫面 */\n" +
"#screenIdle{background:var(--bg-idle);gap:20px;}\n" +
".idle-logo{font-size:clamp(48px,8vw,96px);}\n" +
".idle-store{font-size:clamp(28px,4vw,56px);font-weight:800;color:#fff;letter-spacing:.04em;}\n" +
".idle-msg{font-size:clamp(16px,2.2vw,30px);color:#94a3b8;margin-top:4px;}\n" +
".idle-time{font-size:clamp(14px,1.6vw,22px);color:#475569;margin-top:28px;\n" +
"  font-variant-numeric:tabular-nums;}\n" +
"/* 待機輪播：滿版背景圖 */\n" +
".idle-slides{position:fixed;inset:0;z-index:0;}\n" +
".idle-slides img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;\n" +
"  opacity:0;transition:opacity 1s ease;}\n" +
".idle-slides img.show{opacity:1;}\n" +
".idle-overlay{position:relative;z-index:1;display:flex;flex-direction:column;\n" +
"  align-items:center;justify-content:center;gap:20px;\n" +
"  background:rgba(15,23,42,.55);width:100%;height:100%;}\n" +
".idle-bar{position:fixed;bottom:0;left:0;right:0;height:4px;z-index:2;\n" +
"  background:linear-gradient(90deg,#2563eb,#10b981,#f59e0b,#2563eb);\n" +
"  background-size:300% 100%;animation:shimmer 4s linear infinite;}\n" +
"@keyframes shimmer{0%{background-position:0% 0%}100%{background-position:300% 0%}}\n" +
"\n" +
"/* 購物車畫面：左購物車 + 右輪播 */\n" +
"#screenCart{background:var(--bg-cart);color:var(--text-dark);\n" +
"  flex-direction:row;align-items:stretch;justify-content:stretch;padding:0;}\n" +
".cart-left{flex:0 0 55%;display:flex;flex-direction:column;\n" +
"  padding:clamp(14px,2.5vw,40px);overflow:hidden;}\n" +
".cart-header{display:flex;align-items:center;gap:12px;margin-bottom:16px;}\n" +
".cart-store{font-size:clamp(16px,2vw,28px);font-weight:800;color:var(--primary);}\n" +
".cart-subtitle{font-size:clamp(12px,1.3vw,18px);color:#64748b;}\n" +
".cart-items{flex:1;overflow-y:auto;display:flex;flex-direction:column;gap:8px;}\n" +
".cart-items::-webkit-scrollbar{width:4px;}\n" +
".cart-items::-webkit-scrollbar-thumb{background:#cbd5e1;border-radius:2px;}\n" +
".cart-item{display:flex;align-items:flex-start;gap:10px;padding:10px 14px;\n" +
"  background:#fff;border-radius:12px;border:1px solid var(--line);\n" +
"  box-shadow:0 1px 3px rgba(0,0,0,.05);}\n" +
".ci-qty{flex-shrink:0;min-width:30px;height:30px;background:var(--primary);\n" +
"  color:#fff;border-radius:8px;display:flex;align-items:center;\n" +
"  justify-content:center;font-weight:800;font-size:clamp(12px,1.4vw,18px);}\n" +
".ci-info{flex:1;}\n" +
".ci-name{font-size:clamp(14px,1.6vw,22px);font-weight:700;}\n" +
".ci-opts{font-size:clamp(11px,1.1vw,15px);color:#64748b;margin-top:2px;}\n" +
".ci-price{flex-shrink:0;font-size:clamp(13px,1.5vw,20px);font-weight:700;\n" +
"  align-self:center;}\n" +
"/* 左下合計列 */\n" +
".cart-total-bar{flex-shrink:0;margin-top:14px;padding:14px 18px;border-radius:14px;\n" +
"  background:var(--primary);color:#fff;display:flex;align-items:center;\n" +
"  justify-content:space-between;}\n" +
".ctb-label{font-size:clamp(13px,1.4vw,20px);opacity:.9;}\n" +
".ctb-count{font-size:clamp(11px,1.1vw,15px);opacity:.75;\n" +
"  background:rgba(255,255,255,.15);padding:3px 10px;border-radius:999px;margin-left:10px;}\n" +
".ctb-total{font-size:clamp(28px,4vw,56px);font-weight:900;line-height:1;\n" +
"  letter-spacing:-.02em;}\n" +
"/* 右輪播 */\n" +
".cart-right{flex:0 0 45%;background:#0f172a;flex-shrink:0;\n" +
"  position:relative;overflow:hidden;}\n" +
".cart-right.hidden{display:none;}\n" +
".cart-right img{position:absolute;inset:0;width:100%;height:100%;object-fit:contain;\n" +
"  opacity:0;transition:opacity 1s ease;}\n" +
".cart-right img.show{opacity:1;}\n" +
"\n" +
"/* 付款完成畫面 */\n" +
"#screenPaid{background:var(--bg-paid);gap:18px;}\n" +
".paid-icon{font-size:clamp(60px,9vw,112px);animation:popIn .5s cubic-bezier(.34,1.56,.64,1);}\n" +
"@keyframes popIn{0%{transform:scale(.3);opacity:0}100%{transform:scale(1);opacity:1}}\n" +
".paid-title{font-size:clamp(26px,4vw,58px);font-weight:900;color:#fff;}\n" +
".paid-amount{font-size:clamp(20px,3vw,46px);font-weight:700;color:#6ee7b7;}\n" +
".paid-method{font-size:clamp(13px,1.5vw,22px);color:#a7f3d0;\n" +
"  background:rgba(255,255,255,.1);padding:6px 18px;border-radius:999px;}\n" +
".paid-thank{font-size:clamp(13px,1.5vw,20px);color:#6ee7b7;margin-top:6px;}\n" +
".paid-cd{margin-top:14px;display:flex;flex-direction:column;align-items:center;gap:6px;}\n" +
".paid-cd svg{transform:rotate(-90deg);}\n" +
".cd-track{fill:none;stroke:rgba(255,255,255,.15);stroke-width:4;}\n" +
".cd-bar{fill:none;stroke:#10b981;stroke-width:4;stroke-linecap:round;\n" +
"  transition:stroke-dashoffset 1s linear;}\n" +
".cd-text{font-size:clamp(12px,1.2vw,16px);color:#6ee7b7;}\n" +
"\n" +
"/* 狀態指示 */\n" +
"#connDot{position:fixed;bottom:12px;right:16px;width:10px;height:10px;\n" +
"  border-radius:50%;background:#475569;transition:background .4s;z-index:3;}\n" +
"#connDot.ok{background:#10b981;}#connDot.err{background:#ef4444;}\n" +
"#offlineBanner{position:fixed;bottom:0;left:0;right:0;background:#7f1d1d;\n" +
"  color:#fca5a5;text-align:center;padding:8px;font-size:13px;display:none;z-index:3;}\n" +
"</style>\n" +
"</head>\n" +
"<body>\n" +
"\n" +
"<!-- 待機畫面 -->\n" +
"<div id=\"screenIdle\" class=\"screen active\">\n" +
"  <div class=\"idle-slides\" id=\"idleSlides\"></div>\n" +
"  <div class=\"idle-overlay\">\n" +
"    <div class=\"idle-logo\">&#127869;</div>\n" +
"    <div class=\"idle-store\" id=\"idleStoreName\">" + escapeHtml(storeName) + "</div>\n" +
"    <div class=\"idle-msg\"   id=\"idleMsg\">&#27426;&#36814;&#20809;&#33707;</div>\n" +
"    <div class=\"idle-time\"  id=\"idleClock\"></div>\n" +
"  </div>\n" +
"  <div class=\"idle-bar\"></div>\n" +
"</div>\n" +
"\n" +
"<!-- 購物車畫面 -->\n" +
"<div id=\"screenCart\" class=\"screen\">\n" +
"  <div class=\"cart-left\">\n" +
"    <div class=\"cart-header\">\n" +
"      <div>\n" +
"        <div class=\"cart-store\" id=\"cartStoreName\">" + escapeHtml(storeName) + "</div>\n" +
"        <div class=\"cart-subtitle\">&#30446;&#21069;&#35330;&#21934;&#26126;&#32048;</div>\n" +
"      </div>\n" +
"    </div>\n" +
"    <div class=\"cart-items\" id=\"cartItemList\"></div>\n" +
"    <div class=\"cart-total-bar\">\n" +
"      <div><span class=\"ctb-label\">&#21512;&#35336;</span><span class=\"ctb-count\" id=\"cartCount\">0 &#20214;</span></div>\n" +
"      <div class=\"ctb-total\" id=\"cartTotal\">$0</div>\n" +
"    </div>\n" +
"  </div>\n" +
"  <div class=\"cart-right\" id=\"cartRight\"></div>\n" +
"</div>\n" +
"\n" +
"<!-- 付款完成畫面 -->\n" +
"<div id=\"screenPaid\" class=\"screen\">\n" +
"  <div class=\"paid-icon\">&#9989;</div>\n" +
"  <div class=\"paid-title\">&#24863;&#35613;&#24800;&#3915;&#65281;</div>\n" +
"  <div class=\"paid-amount\"  id=\"paidAmount\">$0</div>\n" +
"  <div class=\"paid-method\"  id=\"paidMethod\">&#29694;&#37329;</div>\n" +
"  <div class=\"paid-thank\"   id=\"paidThank\">&#26399;&#24453;&#24744;&#20877;&#27425;&#20809;&#33707;</div>\n" +
"  <div class=\"paid-cd\">\n" +
"    <svg width=\"48\" height=\"48\" viewBox=\"0 0 48 48\">\n" +
"      <circle class=\"cd-track\" cx=\"24\" cy=\"24\" r=\"20\"/>\n" +
"      <circle class=\"cd-bar\" id=\"paidCdBar\" cx=\"24\" cy=\"24\" r=\"20\"\n" +
"              stroke-dasharray=\"125.66\" stroke-dashoffset=\"0\"/>\n" +
"    </svg>\n" +
"    <div class=\"cd-text\" id=\"paidCdText\">5 &#31186;&#24460;&#22238;&#21040;&#24453;&#27231;</div>\n" +
"  </div>\n" +
"</div>\n" +
"\n" +
"<div id=\"connDot\"></div>\n" +
"<div id=\"offlineBanner\">&#9888;&#65039; &#28961;&#27861;&#36899;&#32218;&#33267; APK &#23458;&#39023; Server</div>\n" +
"\n" +
"<script>\n" +
"var POLL_MS=1000,PAID_STAY=5,CIRC=125.66,SLIDE_MS=4000;\n" +
"var cur='idle',paidTmr=null,paidLeft=0,lastJson='',connOk=false;\n" +
"var slideUrls=[],slideKey='',slideTmr=null,slideIdx=0;\n" +
"function $(id){return document.getElementById(id);}\n" +
"function esc(s){return String(s==null?'':s)\n" +
"  .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')\n" +
"  .replace(/\"/g,'&quot;').replace(/'/g,'&#39;');}\n" +
"function money(v){return '$'+Number(v||0).toLocaleString('zh-TW',{maximumFractionDigits:0});}\n" +
"\n" +
"/* 時鐘 */\n" +
"setInterval(function(){\n" +
"  var n=new Date();\n" +
"  var hh=String(n.getHours()).padStart(2,'0');\n" +
"  var mm=String(n.getMinutes()).padStart(2,'0');\n" +
"  var ss=String(n.getSeconds()).padStart(2,'0');\n" +
"  var wd=['日','一','二','三','四','五','六'][n.getDay()];\n" +
"  var yy=n.getFullYear();\n" +
"  var mo=String(n.getMonth()+1).padStart(2,'0');\n" +
"  var dd=String(n.getDate()).padStart(2,'0');\n" +
"  $('idleClock').textContent=yy+'/'+mo+'/'+dd+'（週'+wd+'）　'+hh+':'+mm+':'+ss;\n" +
"},1000);\n" +
"\n" +
"/* 畫面切換 */\n" +
"function show(name){\n" +
"  if(cur===name)return;\n" +
"  cur=name;\n" +
"  ['idle','cart','paid'].forEach(function(k){\n" +
"    var el=document.getElementById('screen'+k.charAt(0).toUpperCase()+k.slice(1));\n" +
"    if(el)el.classList.toggle('active',k===name);\n" +
"  });\n" +
"}\n" +
"\n" +
"/* 連線指示 */\n" +
"function setConn(ok){\n" +
"  if(connOk===ok)return;\n" +
"  connOk=ok;\n" +
"  $('connDot').className=ok?'ok':'err';\n" +
"  $('offlineBanner').style.display=ok?'none':'block';\n" +
"}\n" +
"\n" +
"/* 輪播圖：依 slides 陣列建立 img，輪流淡入淡出。\n" +
"   key 比對避免每次 poll 都重建 DOM 造成閃爍。 */\n" +
"function setupSlides(urls){\n" +
"  var key=(urls||[]).join('|');\n" +
"  if(key===slideKey)return;\n" +
"  slideKey=key;\n" +
"  slideUrls=Array.isArray(urls)?urls:[];\n" +
"  if(slideTmr){clearInterval(slideTmr);slideTmr=null;}\n" +
"  slideIdx=0;\n" +
"  var right=$('cartRight'),idle=$('idleSlides');\n" +
"  right.innerHTML='';idle.innerHTML='';\n" +
"  if(slideUrls.length===0){\n" +
"    right.classList.add('hidden');\n" +
"    return;\n" +
"  }\n" +
"  right.classList.remove('hidden');\n" +
"  slideUrls.forEach(function(u,i){\n" +
"    var a=document.createElement('img');a.src=u;if(i===0)a.className='show';\n" +
"    a.onerror=function(){a.style.display='none';};\n" +
"    right.appendChild(a);\n" +
"    var b=document.createElement('img');b.src=u;if(i===0)b.className='show';\n" +
"    b.onerror=function(){b.style.display='none';};\n" +
"    idle.appendChild(b);\n" +
"  });\n" +
"  if(slideUrls.length>1){\n" +
"    slideTmr=setInterval(function(){\n" +
"      var rImgs=right.children,iImgs=idle.children;\n" +
"      if(rImgs[slideIdx])rImgs[slideIdx].classList.remove('show');\n" +
"      if(iImgs[slideIdx])iImgs[slideIdx].classList.remove('show');\n" +
"      slideIdx=(slideIdx+1)%slideUrls.length;\n" +
"      if(rImgs[slideIdx])rImgs[slideIdx].classList.add('show');\n" +
"      if(iImgs[slideIdx])iImgs[slideIdx].classList.add('show');\n" +
"    },SLIDE_MS);\n" +
"  }\n" +
"}\n" +
"\n" +
"/* 渲染：購物車 */\n" +
"function renderCart(d){\n" +
"  $('cartStoreName').textContent=d.storeName||'" + escapeJs(storeName) + "';\n" +
"  var items=Array.isArray(d.items)?d.items:[];\n" +
"  var total=Number(d.total||0);\n" +
"  var qty=items.reduce(function(s,x){return s+Number(x.qty||1);},0);\n" +
"  $('cartTotal').textContent=money(total);\n" +
"  $('cartCount').textContent=qty+' 件';\n" +
"  var list=$('cartItemList');list.innerHTML='';\n" +
"  items.forEach(function(item){\n" +
"    var q=Number(item.qty||1);\n" +
"    var up=Number(item.basePrice||0)+Number(item.extraPrice||0);\n" +
"    var opts=(item.selections||[]).map(function(s){return s.moduleName+':'+s.optionName;}).join('　');\n" +
"    var note=item.note?'備註：'+item.note:'';\n" +
"    var sub=[opts,note].filter(Boolean).join('　');\n" +
"    var div=document.createElement('div');div.className='cart-item';\n" +
"    div.innerHTML='<div class=\"ci-qty\">×'+q+'</div>'+\n" +
"      '<div class=\"ci-info\"><div class=\"ci-name\">'+esc(item.name||'')+'</div>'+\n" +
"      (sub?'<div class=\"ci-opts\">'+esc(sub)+'</div>':'')+'</div>'+\n" +
"      '<div class=\"ci-price\">'+money(up*q)+'</div>';\n" +
"    list.appendChild(div);\n" +
"  });\n" +
"  setupSlides(d.slides);\n" +
"}\n" +
"\n" +
"/* 渲染：付款完成 */\n" +
"function renderPaid(d){\n" +
"  $('paidAmount').textContent=money(d.total||0);\n" +
"  $('paidMethod').textContent=d.paymentMethod||'已付款';\n" +
"  $('paidThank').textContent=d.storeName?'感謝光臨 '+d.storeName:'期待您再次光臨';\n" +
"  if(paidTmr)clearInterval(paidTmr);\n" +
"  paidLeft=PAID_STAY;\n" +
"  var bar=$('paidCdBar'),txt=$('paidCdText');\n" +
"  bar.style.strokeDashoffset='0';\n" +
"  txt.textContent=paidLeft+' 秒後回到待機';\n" +
"  paidTmr=setInterval(function(){\n" +
"    paidLeft--;\n" +
"    bar.style.strokeDashoffset=String(CIRC*(1-paidLeft/PAID_STAY));\n" +
"    txt.textContent=paidLeft+' 秒後回到待機';\n" +
"    if(paidLeft<=0){clearInterval(paidTmr);paidTmr=null;show('idle');}\n" +
"  },1000);\n" +
"}\n" +
"\n" +
"/* 渲染：待機 */\n" +
"function renderIdle(d){\n" +
"  $('idleStoreName').textContent=d.storeName||'" + escapeJs(storeName) + "';\n" +
"  $('idleMsg').textContent=d.idleMessage||d.message||'歡迎光臨';\n" +
"  setupSlides(d.slides);\n" +
"}\n" +
"\n" +
"/* 主輪詢 */\n" +
"function poll(){\n" +
"  fetch('/display/state',{cache:'no-store'})\n" +
"  .then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.text();})\n" +
"  .then(function(txt){\n" +
"    setConn(true);\n" +
"    if(txt===lastJson)return;\n" +
"    lastJson=txt;\n" +
"    var d;\n" +
"    try{d=JSON.parse(txt);}catch(e){return;}\n" +
"    var payload=d.data||d;\n" +
"    var type=(payload.type||'idle').toLowerCase();\n" +
"    if(type==='cart'){renderCart(payload);show('cart');}\n" +
"    else if(type==='paid'){renderPaid(payload);show('paid');}\n" +
"    else{renderIdle(payload);if(cur!=='paid')show('idle');}\n" +
"  })\n" +
"  .catch(function(){setConn(false);});\n" +
"}\n" +
"poll();\n" +
"setInterval(poll,POLL_MS);\n" +
"</script>\n" +
"</body>\n" +
"</html>";
    }

    // ==================== Handler：心跳 ====================

    private Response handlePing() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"ok\":true,");
        sb.append("\"service\":\"display\",");
        sb.append("\"port\":").append(DEFAULT_PORT).append(",");
        sb.append("\"version\":\"v20260602\",");
        sb.append("\"stateType\":\"").append(escape(DisplayStateManager.getType())).append("\",");
        sb.append("\"updatedAt\":").append(DisplayStateManager.getUpdatedAt());
        sb.append("}");
        Response r = newFixedLengthResponse(Response.Status.OK,
                "application/json; charset=utf-8", sb.toString());
        return cors(r);
    }

    // ==================== Handler：接收 Web POS 推送 ====================

    private Response handleUpdate(IHTTPSession session) {
        try {
            String body = readBody(session);
            if (body == null || body.trim().isEmpty()) {
                LogManager.w(TAG, "handleUpdate: empty body");
                return cors(json(false, null, "empty body"));
            }
            LogManager.i(TAG, "handleUpdate body=" +
                    (body.length() > 200 ? body.substring(0, 200) + "..." : body));
            DisplayStateManager.update(body);
            return cors(json(true, "{\"saved\":true}", null));
        } catch (Throwable t) {
            LogManager.e(TAG, "handleUpdate failed", t);
            return cors(json(false, null, t.getMessage()));
        }
    }

    // ==================== Handler：客顯 Polling ====================

    private Response handleGetState() {
        String stateJson = DisplayStateManager.getStateJson();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"ok\":true,");
        sb.append("\"data\":").append(stateJson).append(",");
        sb.append("\"updatedAt\":").append(DisplayStateManager.getUpdatedAt());
        sb.append("}");
        Response r = newFixedLengthResponse(Response.Status.OK,
                "application/json; charset=utf-8", sb.toString());
        return cors(r);
    }

    // ==================== Token 驗證 ====================

    private boolean checkToken(IHTTPSession session) {
        if (settings == null) return true;
        String expected = settings.getApiToken();
        if (expected == null || expected.isEmpty()) return true;
        Map<String, String> headers = session.getHeaders();
        String got = headers.get("x-api-token");
        if (got == null) got = headers.get("X-API-Token");
        if (got != null && expected.equals(got)) return true;
        Map<String, List<String>> params = session.getParameters();
        if (params != null) {
            List<String> qs = params.get("token");
            if (qs != null && !qs.isEmpty() && expected.equals(qs.get(0))) return true;
        }
        return false;
    }

    // ==================== 讀取 POST Body ====================

    private String readBody(IHTTPSession session) {
        try {
            Map<String, String> headers = session.getHeaders();
            String cl = headers.get("content-length");
            if (cl == null) cl = headers.get("Content-Length");
            int contentLength = 0;
            if (cl != null) {
                try { contentLength = Integer.parseInt(cl.trim()); } catch (Exception ignored) {}
            }
            java.io.InputStream is = session.getInputStream();
            if (is != null && contentLength > 0) {
                byte[] buf = new byte[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int n = is.read(buf, read, contentLength - read);
                    if (n <= 0) break;
                    read += n;
                }
                return new String(buf, 0, read, "UTF-8");
            }
            Map<String, String> files = new HashMap<>();
            try { session.parseBody(files); } catch (Exception ignored) {}
            String body = files.get("postData");
            return body == null ? "" : body;
        } catch (Throwable t) {
            LogManager.w(TAG, "readBody failed: " + t.getMessage());
            return "";
        }
    }

    // ==================== 工具方法 ====================

    private Response json(boolean ok, String dataJson, String error) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"ok\":").append(ok).append(",");
        sb.append("\"data\":").append(dataJson == null || dataJson.isEmpty() ? "null" : dataJson).append(",");
        sb.append("\"error\":").append(error == null ? "null" : ("\"" + escape(error) + "\""));
        sb.append("}");
        return newFixedLengthResponse(Response.Status.OK,
                "application/json; charset=utf-8", sb.toString());
    }

    private Response cors(Response r) {
        r.addHeader("Access-Control-Allow-Origin", "*");
        r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        r.addHeader("Access-Control-Allow-Headers", "Content-Type, X-API-Token");
        r.addHeader("Access-Control-Allow-Private-Network", "true");
        r.addHeader("Access-Control-Max-Age", "86400");
        return r;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
