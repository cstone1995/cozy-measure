(function () {
  'use strict';
  const KEY = 'cozy_room_v1';
  function parsePlan(raw) {
    try {
      const points=typeof raw==='string'?JSON.parse(raw):raw;
      if(!Array.isArray(points)||points.length<3||points.length>40) return null;
      if(!points.every(p=>p&&[p.x,p.y].every(n=>typeof n==='number'&&Number.isFinite(n)&&Math.abs(n)<=100)))return null;
      const cross=(a,b,c)=>(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);
      let twice=0;
      for(let i=0;i<points.length;i++){
        const a=points[i],b=points[(i+1)%points.length];
        if(Math.hypot(a.x-b.x,a.y-b.y)<0.01)return null;
        twice+=a.x*b.y-b.x*a.y;
        for(let j=i+2;j<points.length;j++){
          if(i===0&&j===points.length-1)continue;
          const c=points[j],d=points[(j+1)%points.length];
          if(cross(a,b,c)*cross(a,b,d)<=0&&cross(c,d,a)*cross(c,d,b)<=0 &&
            Math.max(Math.min(a.x,b.x),Math.min(c.x,d.x))<=Math.min(Math.max(a.x,b.x),Math.max(c.x,d.x)) &&
            Math.max(Math.min(a.y,b.y),Math.min(c.y,d.y))<=Math.min(Math.max(a.y,b.y),Math.max(c.y,d.y)))return null;
        }
      }
      const area=Math.abs(twice)/2;
      return area>=0.01&&area<=10000?{points:points.map(p=>({x:p.x,y:p.y})),area}:null;
    }catch(_){return null;}
  }
  function parseRoom(search) {
    const q = new URLSearchParams(search);
    const rawL = q.get('cozy_length'), rawW = q.get('cozy_width');
    if (!rawL || !rawW) return null;
    const length = Number(rawL), width = Number(rawW);
    if (![length,width].every(n => Number.isFinite(n) && n > 0 && n <= 100)) return null;
    const plan=parsePlan(q.get('cozy_plan'));
    return {plan, name:(q.get('cozy_room') || 'My room').slice(0,60), length, width, savedAt:Date.now()};
  }
  function recommend(length, width, rolls, allowance) {
    if (![length,width].every(n=>Number.isFinite(n)&&n>0)) return null;
    const l=length+(allowance?0.1:0), w=width+(allowance?0.1:0), cuts=[];
    for (const r of rolls) {
      if (![4,5].includes(r)) continue;
      if(w<=r+1e-9)cuts.push({width:r,length:Math.ceil((l-1e-9)*10)/10});
      if(l<=r+1e-9)cuts.push({width:r,length:Math.ceil((w-1e-9)*10)/10});
    }
    return cuts.map(c=>({...c,area:c.width*c.length})).sort((a,b)=>a.area-b.area||a.width-b.width)[0]||null;
  }
  if (typeof module !== 'undefined') module.exports={parseRoom,parsePlan,recommend};
  if (typeof document === 'undefined') return;
  function init() {
    let room=parseRoom(location.search);
    try {
      if(room) localStorage.setItem(KEY,JSON.stringify(room));
      else room=JSON.parse(localStorage.getItem(KEY)||'null');
    } catch (_) {}
    if(!room || ![room.length,room.width].every(n=>Number.isFinite(n)&&n>0&&n<=100)) return;
    if(!Number.isFinite(room.savedAt)||Date.now()-room.savedAt>30*864e5) return;
    room.plan=parsePlan(room.plan?.points);
    try {
      const url=new URL(location.href);
      ['cozy_room','cozy_length','cozy_width','cozy_plan'].forEach(k=>url.searchParams.delete(k));
      history.replaceState(history.state,'',url.pathname+url.search+url.hash);
    }catch(_){}
    const main=document.getElementById('MainContent')||document.querySelector('main');
    if(!main||document.getElementById('cozy-saved-room'))return;
    const box=document.createElement('aside'); box.id='cozy-saved-room';
    box.setAttribute('aria-label','Saved room measurements');
    box.style.cssText='padding:18px 24px;margin:16px auto;max-width:1120px;background:#f5f1eb;border:1px solid #8b7566;border-radius:8px;color:#303a34;font:16px/1.5 sans-serif';
    const text=document.createElement('p');text.textContent=`${room.name}: ${room.length.toFixed(2)}m × ${room.width.toFixed(2)}m · ${room.plan?`Measured outline area ${room.plan.area.toFixed(2)}m²`:`Enclosing rectangle ${(room.length*room.width).toFixed(2)}m²`}. Check measurements before ordering.`;
    const link=document.createElement('a');link.textContent='Review my room sizes';link.href='/pages/room-size-calculator-estimate-what-you-need';
    const clear=document.createElement('button');clear.type='button';clear.textContent='Forget this room';clear.style.marginLeft='20px';
    clear.addEventListener('click',()=>{try{localStorage.removeItem(KEY);sessionStorage.removeItem('cozy_recommended_size');}catch(_){}box.remove();document.dispatchEvent(new CustomEvent('cozy:room-cleared'));});
    box.append(text,link,clear);main.prepend(box);
    const l=document.getElementById('length'),w=document.getElementById('width'),u=document.getElementById('units');
    if(l&&w&&document.getElementById('calculator')){
      if(u){u.value='m';u.dispatchEvent(new Event('change',{bubbles:true}));}
      l.value=String(room.length);w.value=String(room.width);
      [l,w].forEach(el=>el.dispatchEvent(new Event('input',{bubbles:true})));
    }
    document.dispatchEvent(new CustomEvent('cozy:room-loaded',{detail:room}));
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
