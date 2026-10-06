<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Teste da mira</title>
<style>
html,body{margin:0;background:#1c1c1c;color:#eee;font:14px sans-serif}
#bar{padding:8px 10px;display:flex;gap:10px;align-items:center;flex-wrap:wrap}
button{font:inherit;padding:6px 10px}
canvas{display:block;width:100%;touch-action:none;background:#0b5d3b}
#info{padding:6px 10px}
</style></head><body>
<div id="bar"><button id="shuffle">Embaralhar bolas</button><span>Arraste na mesa para mirar</span></div>
<canvas id="c"></canvas>
<div id="info"></div>
<script>
// aim.js — previsão de tacada para o SEU jogo (física ideal: sem atrito, sem efeito).
// Você entrega as posições que o seu motor já tem; nada de ler a tela.
//
//   cue      {x,y}            bola branca
//   dir      {x,y}            direção da tacada (unitária)
//   balls    [{x,y,...}]      demais bolas (sem a branca)
//   B        {l,t,r,b}        limites do CENTRO das bolas (borda interna da mesa menos R)
//   pockets  [{x,y,r}]        caçapas
//   R        raio da bola

function norm(x, y) { const m = Math.hypot(x, y) || 1; return { x: x / m, y: y / m }; }

// Anda a partir de `start` na direção `dir`. Para ao bater em outra bola, cair na caçapa
// ou depois de `maxBounces` tabelas. `ignore` = bolas que não contam como obstáculo.
function trace(start, dir, ignore, balls, B, pockets, R, maxBounces) {
  const p = { x: start.x, y: start.y }, d = { x: dir.x, y: dir.y };
  const pts = [{ x: p.x, y: p.y }];
  for (let i = 0; i <= maxBounces; i++) {
    let sw = Infinity, axis = "";
    if (d.x > 1e-9) { sw = (B.r - p.x) / d.x; axis = "x"; }
    else if (d.x < -1e-9) { sw = (B.l - p.x) / d.x; axis = "x"; }
    if (d.y > 1e-9) { const s = (B.b - p.y) / d.y; if (s < sw) { sw = s; axis = "y"; } }
    else if (d.y < -1e-9) { const s = (B.t - p.y) / d.y; if (s < sw) { sw = s; axis = "y"; } }
    sw = Math.max(0, sw);

    let sb = Infinity, hb = null;
    for (const o of balls) {
      if (ignore.includes(o)) continue;
      const fx = o.x - p.x, fy = o.y - p.y, proj = fx * d.x + fy * d.y;
      if (proj <= 0) continue;
      const perp2 = fx * fx + fy * fy - proj * proj;
      if (perp2 >= 4 * R * R) continue;
      const s = proj - Math.sqrt(4 * R * R - perp2);
      if (s >= 0 && s < sb) { sb = s; hb = o; }
    }
    const seg = Math.min(sw, sb);

    for (const q of pockets) {
      const t = Math.max(0, Math.min(seg, (q.x - p.x) * d.x + (q.y - p.y) * d.y));
      if (Math.hypot(p.x + d.x * t - q.x, p.y + d.y * t - q.y) < q.r) {
        pts.push({ x: p.x + d.x * t, y: p.y + d.y * t });
        return { pts, pocket: q, d };
      }
    }
    p.x += d.x * seg; p.y += d.y * seg;
    pts.push({ x: p.x, y: p.y });
    if (hb && sb <= sw) return { pts, hit: hb, d };
    if (axis === "x") d.x = -d.x; else d.y = -d.y;     // rebate na tabela
  }
  return { pts, d };
}

function predict(cue, dir, balls, B, pockets, R, opts = {}) {
  const targetBounces = opts.targetBounces ?? 8;      // "linha infinita": quantas tabelas seguir
  const r1 = trace(cue, dir, [], balls, B, pockets, R, 2);
  if (r1.pocket) return { cuePts: r1.pts, scratch: true };
  if (!r1.hit) return { cuePts: r1.pts };

  const target = r1.hit, contact = r1.pts[r1.pts.length - 1];
  const n = norm(target.x - contact.x, target.y - contact.y);   // sentido da bola atingida

  // trajetória da bola atingida
  const r2 = trace(target, n, [target], balls, B, pockets, R, targetBounces);

  // desvio da branca (regra dos 90°)
  const dot = r1.d.x * n.x + r1.d.y * n.y;
  const tx = r1.d.x - dot * n.x, ty = r1.d.y - dot * n.y, tm = Math.hypot(tx, ty);
  const r3 = tm > 0.05 ? trace(contact, { x: tx / tm, y: ty / tm }, [target], balls, B, pockets, R, 1) : null;

  return {
    cuePts: r1.pts, contact, target,
    targetPts: r2.pts, targetPocket: r2.pocket || null, targetHitsBall: r2.hit || null,
    cueAfterPts: r3 ? r3.pts : null, cueAfterPocket: r3 ? r3.pocket || null : null,
  };
}

// ---------- mesa de teste (substitua pelos dados do seu jogo) ----------
const c=document.getElementById("c"),ctx=c.getContext("2d"),info=document.getElementById("info");
const W=Math.min(innerWidth,900),H=Math.round(W/2),R=W*0.016,M=W*0.05;
c.width=W;c.height=H;c.style.height=H+"px";
const B={l:M+R,t:M+R,r:W-M-R,b:H-M-R};
const pockets=[[M,M],[W/2,M-R*0.4],[W-M,M],[M,H-M],[W/2,H-M+R*0.4],[W-M,H-M]].map(([x,y])=>({x,y,r:R*1.9}));
let cue={x:W*0.25,y:H/2},balls=[],aim={x:1,y:0};
function shuffle(){
  balls=[];
  for(let i=0;i<7;i++){
    for(let t=0;t<50;t++){
      const b={x:W*0.45+Math.random()*W*0.45,y:B.t+Math.random()*(B.b-B.t)};
      if(balls.every(o=>Math.hypot(o.x-b.x,o.y-b.y)>R*2.3)){balls.push(b);break}
    }
  }
  draw();
}
function dot(x,y,r,col){ctx.fillStyle=col;ctx.beginPath();ctx.arc(x,y,r,0,7);ctx.fill()}
function line(pts,col,dash){
  ctx.save();ctx.strokeStyle=col;ctx.lineWidth=2;ctx.setLineDash(dash?[8,6]:[]);
  ctx.beginPath();pts.forEach((q,i)=>i?ctx.lineTo(q.x,q.y):ctx.moveTo(q.x,q.y));ctx.stroke();ctx.restore();
}
function draw(){
  ctx.clearRect(0,0,W,H);
  ctx.fillStyle="#3a2412";ctx.fillRect(0,0,W,H);
  ctx.fillStyle="#0b5d3b";ctx.fillRect(M,M,W-2*M,H-2*M);
  pockets.forEach(q=>dot(q.x,q.y,q.r*0.8,"#000"));
  balls.forEach(b=>dot(b.x,b.y,R,"#ffd23c"));
  dot(cue.x,cue.y,R,"#fff");
  const r=predict(cue,aim,balls,B,pockets,R);
  line(r.cuePts,"#fff",true);
  let msg="sem contato";
  if(r.scratch)msg="a branca cai na caçapa";
  else if(r.target){
    ctx.strokeStyle="#fff";ctx.lineWidth=2;ctx.beginPath();ctx.arc(r.contact.x,r.contact.y,R,0,7);ctx.stroke();
    line(r.targetPts,"#ff5ad2",false);
    const e=r.targetPts[r.targetPts.length-1];dot(e.x,e.y,R*0.5,"#ff5ad2");
    if(r.cueAfterPts)line(r.cueAfterPts,"#50dcff",true);
    msg=r.targetPocket?"a bola atingida cai na caçapa":r.targetHitsBall?"a bola atingida bate em outra bola":"a bola atingida rebate nas tabelas";
  }
  info.textContent=msg;
}
function point(e){const rc=c.getBoundingClientRect();return{x:(e.clientX-rc.left)*W/rc.width,y:(e.clientY-rc.top)*H/rc.height}}
function setAim(e){const p=point(e);aim=norm(p.x-cue.x,p.y-cue.y);draw()}
c.addEventListener("pointerdown",e=>{c.setPointerCapture(e.pointerId);setAim(e)});
c.addEventListener("pointermove",e=>{if(e.buttons)setAim(e)});
document.getElementById("shuffle").onclick=shuffle;
shuffle();
</script></body></html>
