// Owned procedural height relief over Mauro's existing owned paintings.
const fs=require('fs'), path=require('path');
const sharp=require(process.env.TILES_NODE_MODULES+'/sharp');
const terrains=['timber','clay','fleece','grain','stone','desert'];
const dir='docs/design/tiles-3d', res='app/src/main/res/drawable-nodpi';
async function main(){
fs.mkdirSync(dir,{recursive:true});
for(const t of terrains){
 const {data,info}=await sharp(`${res}/tile_${t}.webp`).resize(384,444).ensureAlpha().raw().toBuffer({resolveWithObject:true});
 const out=Buffer.from(data), height=new Float32Array(info.width*info.height);
 for(let y=0;y<info.height;y++)for(let x=0;x<info.width;x++){
  const i=(y*info.width+x)*4;
  // Color-derived proxy height: restrained brush texture, not recovered scene geometry.
  height[y*info.width+x]=(data[i]*.21+data[i+1]*.72+data[i+2]*.07)/255;
 }
 for(let y=1;y<info.height-1;y++)for(let x=1;x<info.width-1;x++){
  const p=y*info.width+x,i=p*4;
  const shade=1+ .36*(height[p-1]-height[p+1]+height[p-info.width]-height[p+info.width]);
  for(let c=0;c<3;c++)out[i+c]=Math.max(0,Math.min(255,data[i+c]*shade));
 }
 await sharp(out,{raw:info}).png().toFile(`${dir}/${t}-relief.png`);
 await sharp(out,{raw:info}).webp({quality:94}).toFile(`${res}/relief_${t}.webp`);
 for(const angle of [0,35,60])await sharp(out,{raw:info}).resize(384,Math.round(444*Math.cos(angle*Math.PI/180)),{fit:'fill'}).png().toFile(`${dir}/${t}-${angle}.png`);
}
let html=`<!doctype html><meta charset="utf-8"><title>Tile depth and life palette</title><style>
body{margin:24px;background:repeating-linear-gradient(3deg,#23180f,#302217 10px,#241a12 14px);color:#f5e4bd;font:16px Georgia}section{border:2px solid #a78949;padding:18px;margin:18px 0;background:#302217}h1,h2{color:#e1c17e}button{background:#493521;color:#e1c17e;border:2px solid #a78949;padding:8px}canvas{width:230px;height:210px}.row{display:flex;gap:8px}.cell{text-align:center}small{display:block;max-width:1100px}</style>
<h1>Working German Board Game · tile depth + life</h1><p>A1 recommended: continuous side geometry + baked brush relief. A2: nearest of three raster angles, same side band.</p><small>Relief is a restrained color-derived height proxy, not a reconstructed normal map. Upper-left light. A2 snaps at 17.5° and 47.5°: extra assets and visible transitions. Both retain existing owned painting. Life marks are procedural accents; placement must be checked on device against each painting.</small><p>B1 life: Grain stems sway · Timber leaves drift · Fleece sheep shift · Stone glints shimmer · Clay dust breathes. Desert stays still. 8-second deterministic loops, density 3 (0 = off).</p><button onclick="running=!running;frozen=performance.now()/1000">Pause / resume life</button>`;
for(const t of terrains){html+=`<section><h2>${t[0].toUpperCase()+t.slice(1)}</h2><div class="row">`;
for(const option of [1,2])for(const angle of [0,35,60])html+=`<div class="cell">A${option} · ${angle}°<br><canvas data-terrain="${t}" data-option="${option}" data-angle="${angle}" width="460" height="420"></canvas></div>`;
html+='</div></section>'}
html+=`<script>
let running=true,frozen=0; const images={}, corners=Array.from({length:6},(_,i)=>[Math.cos((-90+i*60)*Math.PI/180)*.93,Math.sin((-90+i*60)*Math.PI/180)*.93]);
const types=['timber','clay','fleece','grain','stone','desert'];
Promise.all(types.flatMap(t=>[0,35,60,'relief'].map(a=>new Promise(resolve=>{let im=new Image;im.onload=()=>{images[t+'-'+a]=im;resolve()};im.src='tiles-3d/'+t+'-'+a+'.png'})))).then(()=>{window.paletteReady=true;requestAnimationFrame(frame)});
function frame(ms){const time=(window.STILL_TIME!==undefined?window.STILL_TIME:(running?ms/1000:frozen));document.querySelectorAll('canvas').forEach(c=>{
let ctx=c.getContext('2d'),t=c.dataset.terrain,a=+c.dataset.angle,q=Math.cos(a*Math.PI/180),r=165,cx=230,cy=185,depth=r*(.055+.20*Math.sin(a*Math.PI/180));ctx.clearRect(0,0,460,420);
for(let i=1;i<=3;i++){let p=corners[i],b=corners[i+1],light=.42+.22*(-Math.cos((-60+i*60)*Math.PI/180)-Math.sin((-60+i*60)*Math.PI/180))/Math.sqrt(2);ctx.fillStyle='rgb('+[130,100,65].map(v=>Math.round(v*light)).join(',')+')';ctx.beginPath();ctx.moveTo(cx+p[0]*r,cy+p[1]*r*q);ctx.lineTo(cx+b[0]*r,cy+b[1]*r*q);ctx.lineTo(cx+b[0]*r,cy+b[1]*r*q+depth);ctx.lineTo(cx+p[0]*r,cy+p[1]*r*q+depth);ctx.fill()}
let im=images[t+'-'+(c.dataset.option==='1'?'relief':a)];ctx.drawImage(im,cx-Math.sqrt(3)*r*.93/2,cy-r*.93*q,Math.sqrt(3)*r*.93,2*r*.93*q);
for(let i=0;i<6;i++){let p=corners[i],b=corners[(i+1)%6];ctx.strokeStyle=i>=4?'#fff0be70':'#33231760';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(cx+p[0]*r,cy+p[1]*r*q);ctx.lineTo(cx+b[0]*r,cy+b[1]*r*q);ctx.stroke()}
if(t!=='desert')for(let j=0;j<3;j++){let w=Math.sin((time/8+((types.indexOf(t)*17+j*7)%31)/31)*Math.PI*2),x=cx+(-.08+j*.16)*r,y=cy+(.28+j%2*.16)*r*q;ctx.fillStyle={grain:'#f7d977c0',timber:'#4d943d80',fleece:'#fff2ddc0',stone:'#e3eff470',clay:'#8a654f40'}[t];ctx.strokeStyle=ctx.fillStyle;ctx.lineWidth=r*(t==='grain'?.013:.003+.003*(w+1));ctx.beginPath();if(t==='grain'||t==='stone'){ctx.moveTo(x,y);ctx.lineTo(x+(t==='grain'?w*.022:.045)*r,y-(t==='grain'?.09:.018)*r*q);ctx.stroke()}else{if(t==='fleece'){ctx.ellipse(x+w*.012*r,y,.023*r,.017*r*q,0,0,Math.PI*2);ctx.fill();ctx.fillStyle='#4c463ab0';ctx.beginPath();ctx.arc(x+w*.012*r+.021*r,y,.009*r,0,Math.PI*2);ctx.fill()}else{ctx.arc(x+w*(t==='clay'?.02:.012)*r,y-(t==='clay'?(w+1)*.025*r*q:0),r*(t==='clay'?.012+.008*(w+1):.022),0,Math.PI*2);ctx.fill()}}}
});if(running)requestAnimationFrame(frame);else setTimeout(()=>requestAnimationFrame(frame),100)}
</script>`;
fs.writeFileSync('docs/design/tiles-3d-palette.html',html);
}
main().catch(e=>{console.error(e);process.exit(1)});
