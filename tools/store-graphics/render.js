// JS-Port von ClockRenderer.kt – gleiche Maße und Farben, für Store-Grafiken.
const P = (bg, off, offStroke, text1, text2, panel, panelStroke, ink, accent, glow) => ({ bg, off, offStroke, text1, text2, panel, panelStroke, ink, accent, glow });
const STYLES = {
  CLASSIC: { dark: P('#0E0F13','rgba(255,255,255,0.08)','rgba(255,255,255,0.08)','#8A8780','#6B6963',0,0,0,'#FFB347',true), light: P('#F4F1EA','rgba(0,0,0,0.09)','rgba(0,0,0,0.09)','#5E5A53','#86827A',0,0,0,'#E0890B',false) },
  BINARY: { dark: P('#050B14',0,'#22344A','#6F8296','#4A5A6B',0,0,0,'#5CE1E6',true), light: P('#EEF3F7',0,'#BFCDDA','#4F6274','#7A8B9B',0,0,0,'#0B8C9C',false) },
  LED: { dark: P('#161815','#1F231D',0,'#8A9585','#5A6356','#0D0F0C','#262A24',0,'#7CFF6B',true), light: P('#E4E8E1','#DDE2D8',0,'#5D6658','#7C8576','#F8F9F6','#C9CFC4',0,'#2E9E4A',false) },
  PAPER: { dark: P('#1B1A18',0,'rgba(237,232,223,0.25)','#EDE8DF','#A39E94',0,0,'#EDE8DF','#FF6A4D',false), light: P('#EDE8DF',0,'rgba(26,26,26,0.22)','#1A1A1A','#5C574F',0,0,'#1A1A1A','#C93A24',false) },
  RINGS: { dark: P('#0B0D12','#1B1F28',0,'#C9CCD6','#6A7080',0,0,0,'#FF6B9A',true), light: P('#F7F3F5','#E7DEE3',0,'#2C2A30','#7D7580',0,0,0,'#D23A70',false) },
  TERMINAL: { dark: P('#070B08','#1C3324',0,'#7FB892','#3F6B4D',0,0,0,'#39FF88',true), light: P('#F0F4EE','#C6D4C9',0,'#2F5A3C','#6E8574',0,0,0,'#15803D',false) },
  BARS: { dark: P('#111216','#24262E',0,'#9A98A6','#696776',0,0,0,'#B69CFF',true), light: P('#F5F4F8','#E2E0EA',0,'#4C4A57','#807E8C',0,0,0,'#6A4FD6',false) },
};
const MONO = '"DejaVu Sans Mono", monospace';
const SANS = '"DejaVu Sans", sans-serif';
const bcd = (t, sec) => { const g = [[Math.floor(t.h/10),2],[t.h%10,4],[Math.floor(t.m/10),3],[t.m%10,4]]; if (sec) g.push([Math.floor(t.s/10),3],[t.s%10,4]); return g.map(([v,bits])=>({v,bits})); };
const bin = (t, sec) => { const g=[{v:t.h,bits:5},{v:t.m,bits:6}]; if (sec) g.push({v:t.s,bits:6}); return g; };
const on = (g,b) => ((g.v>>b)&1)===1;
const two = n => String(n).padStart(2,'0');
const timeText = (t,s) => s ? `${two(t.h)}:${two(t.m)}:${two(t.s)}` : `${two(t.h)}:${two(t.m)}`;
const cg = i => (i%2===1?28:14), lg = i => (i%2===1?20:10);
const gw = (n, cell, gap) => { let w=n*cell; for (let i=0;i<n-1;i++) w+=gap(i); return w; };
const LABELS=['H','M','S'];
function rr(c,x,y,w,h,r){c.beginPath();c.roundRect(x,y,w,h,r);}
function hexA(col,a){ if(col.startsWith('#')) return col+a; return col; }
function onFill(c,F,blur,a){ c.fillStyle=F.accent; if(F.p.glow){c.shadowColor=hexA(F.accent,a);c.shadowBlur=blur;} else c.shadowBlur=0; }
function plain(c,col){ c.shadowBlur=0; c.fillStyle=col; }
function pstroke(c,col,w){ c.shadowBlur=0; c.strokeStyle=col; c.lineWidth=w; }
function tx(c,fam,bold,size,col,align,sp){ c.shadowBlur=0; c.font=`${bold?'bold ':''}${size}px ${fam}`; c.fillStyle=col; c.textAlign=align; c.letterSpacing=(sp*size)+'px'; }
const ringOuter = n => 58+(n-1)*32+10+8;
function ledPanel(s){ const n=s.showSeconds?6:4; return {w:40+(s.showHints?24:0)+gw(n,38,lg), h:44+4*38+24+(s.showHints?38:0)}; }
function measure(s,hasDate){
  const rows=s.showSeconds?3:2, cols=s.showSeconds?6:4;
  switch(s.style){
    case 'CLASSIC': { let h=178; if(s.showHints||hasDate)h+=36; if(s.showHints)h+=20; if(s.showHints&&hasDate)h+=6; if(hasDate)h+=16; return {w:Math.max(gw(cols,34,cg),hasDate?230:0),h}; }
    case 'BINARY': { let h=rows*22+(rows-1)*22; if(s.showHints)h+=66; if(hasDate)h+=(s.showHints?8:14)+14; return {w:314,h}; }
    case 'LED': { const p=ledPanel(s); return {w:p.w,h:p.h+(hasDate?34:0)}; }
    case 'PAPER': { let h=rows*34+(rows-1)*28; if(hasDate)h+=56; return {w:264+(s.showHints?76:0),h}; }
    case 'RINGS': { const d=2*ringOuter(rows); return {w:Math.max(d,hasDate?230:0),h:d+(hasDate?38:0)}; }
    case 'TERMINAL': { let h=31+rows*40+(rows-1)*8; if(hasDate)h+=30; return {w:30+168+(s.showHints?92:0),h}; }
    case 'BARS': { let h=4*14+36; if(s.showHints)h+=32; if(hasDate)h+=28; return {w:Math.max(gw(cols,38,cg),hasDate?230:0),h}; }
  }
}
function drawClock(c,W,H,s,t,date,o={}){
  const p=STYLES[s.style][o.dark?'dark':'light']; const accent=s.accent||p.accent;
  if(!o.noBackground){ c.fillStyle=p.bg; if(o.radius){rr(c,0,0,W,H,o.radius);c.fill();} else c.fillRect(0,0,W,H); }
  if(!s.showDate) date=null;
  const d=measure(s,!!date); const pl=o.placement; const maxScale=o.maxScale??W/390;
  const fit = pl? Math.min(W*0.96/d.w,H*0.96/d.h) : Math.min(W*0.86/d.w,H*0.82/d.h);
  const sc=Math.min(fit,maxScale*(pl?pl.size:1));
  const bw=d.w*sc,bh=d.h*sc;
  const left=Math.min(Math.max(W*(pl?pl.x:0.5)-bw/2,0),Math.max(W-bw,0));
  const top=Math.min(Math.max(H*(pl?pl.y:0.5)-bh/2,0),Math.max(H-bh,0));
  c.save(); c.translate(left,top); c.scale(sc,sc);
  const F={s,p,accent,t,date,d};
  ({CLASSIC:classic,BINARY:binary,LED:led,PAPER:paper,RINGS:rings,TERMINAL:terminal,BARS:bars})[s.style](c,F);
  c.restore(); c.shadowBlur=0;
}
function classic(c,F){ const {s,p,t,d}=F; const g=bcd(t,s.showSeconds); let x=(d.w-gw(g.length,34,cg))/2;
  g.forEach((gr,i)=>{ for(let r=0;r<4;r++){const b=3-r; if(b>=gr.bits)continue; if(on(gr,b))onFill(c,F,18,'99'); else plain(c,p.off); c.beginPath(); c.arc(x+17,r*48+17,17,0,7); c.fill();} x+=34+(i<g.length-1?cg(i):0); });
  let y=214; if(s.showHints){tx(c,MONO,0,15,p.text1,'center',0.27); c.fillText(timeText(t,s.showSeconds),d.w/2,y+15); y+=26;}
  if(F.date){tx(c,MONO,0,12,p.text2,'center',0); c.fillText(F.date,d.w/2,y+12);} }
function binary(c,F){ const {s,p,t}=F; const x0=36; let y=0;
  if(s.showHints){tx(c,MONO,0,11,p.text2,'center',0); for(let i=0;i<6;i++)c.fillText(String(1<<(5-i)),x0+i*48+19,11); y=36;}
  bin(t,s.showSeconds).forEach((gr,k)=>{ tx(c,MONO,1,14,p.text1,'left',0); c.fillText(LABELS[k],0,y+16);
    for(let i=0;i<6;i++){const b=5-i; if(b>=gr.bits)continue; const px=x0+i*48; if(on(gr,b)){onFill(c,F,14,'80'); rr(c,px,y,38,22,11); c.fill();} else {pstroke(c,p.offStroke,1.5); rr(c,px+.75,y+.75,36.5,20.5,11); c.stroke();}}
    y+=44; }); y-=22;
  if(s.showHints){tx(c,MONO,0,13,p.text1,'left',0.23); c.fillText(timeText(t,s.showSeconds),x0,y+27); y+=30;}
  if(F.date){y+=s.showHints?8:14; tx(c,MONO,0,12,p.text2,'left',0); c.fillText(F.date,x0,y+12);} }
function led(c,F){ const {s,p,t,d}=F; const P=ledPanel(s); plain(c,p.panel); rr(c,0,0,P.w,P.h,24); c.fill(); pstroke(c,p.panelStroke,1); rr(c,.5,.5,P.w-1,P.h-1,24); c.stroke();
  let x=20; if(s.showHints){tx(c,MONO,0,10,p.text2,'center',0); for(let r=0;r<4;r++)c.fillText(String(1<<(3-r)),x+7,22+r*46+23); x+=24;}
  const g=bcd(t,s.showSeconds); g.forEach((gr,i)=>{ for(let r=0;r<4;r++){const b=3-r; if(b>=gr.bits)continue; if(on(gr,b))onFill(c,F,16,'88'); else plain(c,p.off); rr(c,x,22+r*46,38,38,8); c.fill();}
    if(s.showHints){tx(c,MONO,1,20,F.accent,'center',0); c.fillText(String(gr.v),x+19,22+176+32);} x+=38+(i<g.length-1?lg(i):0); });
  if(F.date){tx(c,MONO,0,11,p.text2,'center',0.18); c.fillText(F.date.toUpperCase(),d.w/2,P.h+31);} }
function paper(c,F){ const {s,p,t,d}=F; let y=0; if(F.date){tx(c,SANS,0,13,p.text2,'left',0.23); c.fillText(F.date.toUpperCase(),0,13); y=56;}
  bin(t,s.showSeconds).forEach((gr,k)=>{ const col=k===2?F.accent:p.ink; for(let i=0;i<6;i++){const b=5-i; if(b>=gr.bits)continue; c.beginPath(); if(on(gr,b)){plain(c,col); c.arc(i*46+17,y+17,17,0,7); c.fill();} else {pstroke(c,p.offStroke,2); c.arc(i*46+17,y+17,16,0,7); c.stroke();}}
    if(s.showHints){tx(c,SANS,1,30,col,'right',0); c.fillText(two(gr.v),d.w,y+28);} y+=62; }); }
function rings(c,F){ const {s,p,t,d}=F; const g=bin(t,s.showSeconds); const cx=d.w/2, cy=ringOuter(g.length); c.lineCap='butt';
  g.forEach((gr,k)=>{ const R=58+k*32, seg=360/gr.bits; for(let i=0;i<gr.bits;i++){ const b=gr.bits-1-i; if(on(gr,b)){c.strokeStyle=F.accent; c.lineWidth=20; if(p.glow){c.shadowColor=hexA(F.accent,'88'); c.shadowBlur=14;} else c.shadowBlur=0;} else pstroke(c,p.off,20);
    const a0=(-90+i*seg+3)*Math.PI/180, a1=(-90+(i+1)*seg-3)*Math.PI/180; c.beginPath(); c.arc(cx,cy,R,a0,a1); c.stroke(); } }); c.shadowBlur=0;
  if(s.showHints){const sz=s.showSeconds?15:22; tx(c,MONO,1,sz,p.text1,'center',0); c.fillText(timeText(t,s.showSeconds),cx,cy+sz*0.36);}
  if(F.date){tx(c,MONO,0,12,p.text2,'center',0); c.fillText(F.date,cx,2*cy+36);} }
function terminal(c,F){ const {s,p,t}=F; tx(c,MONO,0,13,p.text2,'left',0); c.fillText('$ binary-clock',0,13); let y=31;
  bin(t,s.showSeconds).forEach((gr,k)=>{ tx(c,MONO,1,14,p.text2,'left',0); c.fillText(LABELS[k],0,y+27);
    for(let i=0;i<6;i++){const b=5-i; if(b>=gr.bits)continue; const o=on(gr,b); tx(c,MONO,1,34,o?F.accent:p.off,'center',0); if(o&&p.glow){c.shadowColor=hexA(F.accent,'99'); c.shadowBlur=12;} c.fillText(o?'1':'0',30+i*28+14,y+32); c.shadowBlur=0;}
    if(s.showHints){tx(c,MONO,0,18,p.text1,'left',0); c.fillText('= '+two(gr.v),30+168+18,y+28);} y+=48; }); y-=8;
  if(F.date){tx(c,MONO,0,12,p.text2,'left',0); c.fillText('# '+F.date,0,y+28);} }
function bars(c,F){ const {s,p,t,d}=F; const g=bcd(t,s.showSeconds); let x=(d.w-gw(g.length,38,cg))/2; const gh=92;
  g.forEach((gr,i)=>{ for(let r=0;r<4;r++){const b=3-r; if(b>=gr.bits)continue; if(on(gr,b))onFill(c,F,12,'88'); else plain(c,p.off); rr(c,x,r*26,38,14,7); c.fill();}
    if(s.showHints){tx(c,MONO,0,14,p.text1,'center',0); c.fillText(String(gr.v),x+19,gh+28);} x+=38+(i<g.length-1?cg(i):0); });
  if(F.date){const y=gh+(s.showHints?32:0)+14; tx(c,MONO,0,12,p.text2,'center',0); c.fillText(F.date,d.w/2,y+12);} }
