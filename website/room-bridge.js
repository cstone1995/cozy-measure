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
    return {plan, allowance:q.get("cozy_allowance")!=="0", name:(q.get('cozy_room') || 'My room').slice(0,60), length, width, savedAt:Date.now()};
  }
  function recommend(length, width, rolls, allowance, limits = {}) {
    if (![length,width].every(n=>Number.isFinite(n)&&n>0)) return null;
    const l=length+(allowance?0.1:0), w=width+(allowance?0.1:0), cuts=[];
    for (const r of rolls) {
      if (![4,5].includes(r)) continue;
      if(w<=r+1e-9)cuts.push({width:r,length:Math.ceil((l-1e-9)*10)/10});
      if(l<=r+1e-9)cuts.push({width:r,length:Math.ceil((w-1e-9)*10)/10});
    }
    return cuts.map(c=>({...c,length:Math.ceil((Math.max(c.length,limits.minLength||0)-1e-9)*10)/10})).filter(c=>c.length<=(limits.maxLength||100)+1e-9).map(c=>({...c,area:c.width*c.length})).sort((a,b)=>a.area-b.area||a.width-b.width)[0]||null;
  }
  function productRules(response) {
    const d=response?.message==='success'&&response.data?.[0];
    if(!d || Number(d.measurement_type?.id)!==6 || d.length_unit!=='m' || d.width_unit!=='m' || d.measurement_unit!=='sq m' || d.enable_custom_formula!=='off')return null;
    const minLength=Number(d.min_length),maxLength=Number(d.max_length),minWidth=Number(d.min_width),maxWidth=Number(d.max_width);
    if(![minLength,maxLength,minWidth,maxWidth].every(Number.isFinite)||minLength<=0||maxLength<minLength||maxLength>100)return null;
    if(![4,5].includes(minWidth)||![4,5].includes(maxWidth)||minWidth>maxWidth)return null;
    return {minLength,maxLength,rolls:[4,5].filter(n=>n>=minWidth&&n<=maxWidth)};
  }
  if (typeof module !== 'undefined') module.exports={parseRoom,parsePlan,recommend,productRules};
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
      ['cozy_room','cozy_length','cozy_width','cozy_plan','cozy_allowance'].forEach(k=>url.searchParams.delete(k));
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
    if(/\/products\//.test(location.pathname)) setupProduct(room,box);
  }
  async function setupProduct(room,box) {
    let active=true,edited=false;
    const onEdit=e=>{if(e.isTrusted&&['eo-sh-length','eo-sh-width'].includes(e.target?.id))edited=true;};
    document.addEventListener('input',onEdit,true);
    document.addEventListener('cozy:room-cleared',()=>{active=false;document.removeEventListener('input',onEdit,true);},{once:true});
    const status=document.createElement('p');status.setAttribute('role','status');status.textContent='Finding the carpet sizes available for this product…';box.append(status);
    const control=document.createElement('label'),allow=document.createElement('input');
    allow.type='checkbox';allow.checked=room.allowance!==false;control.append(allow,document.createTextNode(' Add 10cm fitting allowance to each room dimension'));
    const apply=document.createElement('button');apply.type='button';apply.textContent='Use my saved room sizes';apply.hidden=true;
    box.append(control,apply);
    const fields=()=>{
      const ls=document.querySelectorAll('#eo-sh-length'),ws=document.querySelectorAll('#eo-sh-width');
      return ls.length===1&&ws.length===1?{l:ls[0],w:ws[0]}:null;
    };
    try {
      const shop=window.Shopify?.shop,handle=location.pathname.match(/\/products\/([^/]+)/)?.[1];
      const id=document.querySelector('input[name="product-id"]')?.value;
      const collections=window.pageData?.productCollections;
      if(!shop||!handle||!id||!Array.isArray(collections))throw Error('Missing product context');
      const query=new URLSearchParams({shop,handle:decodeURIComponent(handle),productId:id,collections:collections.map(c=>c.id).join(',')});
      const response=await fetch('https://sf-pricecalculator.extendons.com/api/storefront/setting?'+query,{signal:AbortSignal.timeout(15000)});
      if(!response.ok)throw Error('Calculator settings unavailable');
      const rules=productRules(await response.json());
      if(!rules)throw Error('Unsupported calculator');
      for(let i=0;i<80&&!fields()&&active;i++)await new Promise(r=>setTimeout(r,250));
      if(!active)return;
      if(!fields())throw Error('Calculator fields unavailable');
      function fill(force=false) {
        if(!active)return;
        const cut=recommend(room.length,room.width,rules.rolls,allow.checked,rules);
        if(!cut){status.textContent='This room needs a join or a different carpet width. Please contact Cozy before ordering.';apply.hidden=true;return;}
        apply.hidden=false;
        status.textContent=Array.from(rules.rolls,w=>w+'m').join(' / ')+' widths · Suggested cut '+cut.width+'m × '+cut.length.toFixed(1)+'m ('+cut.area.toFixed(2)+'m²). '+(allow.checked?'Includes 10cm fitting allowance. ':'No fitting allowance added. ')+(cut.length===rules.minLength?'Product minimum length applies. ':'')+'Check pile direction and the final price before adding to cart.';
        if(edited&&!force)return;
        const f=fields();if(!f)return;
        f.l.value=String(cut.length);f.w.value=String(cut.width);
        for(const el of [f.l,f.w]){el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));}
        document.dispatchEvent(new CustomEvent('cozy:cut-applied',{detail:{room,cut,allowance:allow.checked}}));
      }
      apply.addEventListener('click',()=>{edited=false;fill(true);});
      allow.addEventListener('change',()=>{room.allowance=allow.checked;try{localStorage.setItem(KEY,JSON.stringify(room));}catch(_){}edited=false;fill(true);});
      fill();
    }catch(_){
      if(active){status.textContent='Your room is saved. Enter its sizes in this product’s calculator and check the available widths and minimum length.';control.hidden=true;apply.hidden=true;}
    }
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
