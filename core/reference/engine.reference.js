// Mercato matching + decoy engine - REFERENCE IMPLEMENTATION (JavaScript)
// Source: reference/web-prototype/mercato.html. Port to Rust EXACTLY (see docs/ARCHITECTURE.md).
// Do not approximate normalize/levenshtein/thresholdFor/surnameVariants/matchAnswer/distractorsFor.

function normalize(s){
  return s.normalize("NFD").replace(/[\u0300-\u036f]/g,"")
    .replace(/[ØøŁłÐðÞþ]/g,m=>({"Ø":"O","ø":"o","Ł":"L","ł":"l","Ð":"D","ð":"d","Þ":"T","þ":"t"}[m]))
    .toLowerCase().replace(/[.'’`\-_]/g," ").replace(/[^a-z0-9 ]/g,"")
    .replace(/\s+/g," ").trim();
}
function levenshtein(a,b){
  if(a===b) return 0;
  if(!a.length) return b.length;
  if(!b.length) return a.length;
  let prev=new Array(b.length+1);
  for(let j=0;j<=b.length;j++) prev[j]=j;
  for(let i=1;i<=a.length;i++){
    const cur=[i];
    for(let j=1;j<=b.length;j++){
      const c=a[i-1]===b[j-1]?0:1;
      cur[j]=Math.min(prev[j]+1,cur[j-1]+1,prev[j-1]+c);
    }
    prev=cur;
  }
  return prev[b.length];
}
function thresholdFor(str,base){
  if(str.length<=4) return 0;
  if(str.length<=6) return Math.min(1,base);
  return base;
}
function namesOf(p){
  const set=new Set([p.n.fr,p.n.en,p.n.es].concat(p.alt||[]));
  return Array.from(set);
}
/* Particules de nom : van Dijk, de Bruyne, Di Maria, dos Santos...
   Sans elles, "van dijk" ne correspond a rien. */
const PARTICLES=new Set(["van","von","de","del","della","di","da","do","dos","das",
  "du","des","le","la","el","al","bin","ibn","ter","ten","st","mc","mac","o","van't","der","den"]);

function surnameVariants(name){
  const tk=normalize(name).split(" ").filter(Boolean);
  if(tk.length<2) return [tk[0]||""];
  let i=tk.length-1;
  while(i-1>=1 && PARTICLES.has(tk[i-1])) i--;   // on ne mange jamais le prenom
  const full=tk.slice(i).join(" "), bare=tk[tk.length-1];
  return full===bare?[bare]:[full,bare];
}
function surnameOf(name){ return surnameVariants(name)[0]; }

const SURNAME_INDEX=(()=>{
  const idx={};
  for(const id in PLAYERS){
    for(const nm of [PLAYERS[id].n.fr,PLAYERS[id].n.en,PLAYERS[id].n.es]){
      for(const s of surnameVariants(nm)){
        if(!s) continue;
        (idx[s]=idx[s]||new Set()).add(id);
      }
    }
  }
  const o={}; for(const k in idx) o[k]=Array.from(idx[k]);
  return o;
})();
const AMBIGUOUS=Object.keys(SURNAME_INDEX).filter(s=>SURNAME_INDEX[s].length>1).sort();

/* Index de toutes les formes exactes de la base : noms complets et noms de famille.
   Regle cardinale : une correspondance approchee ne doit jamais l'emporter sur une
   correspondance exacte avec un AUTRE joueur. Sans cela, "kane" passe pour Kante. */
const EXACT_INDEX=(()=>{
  const idx={};
  const add=(k,id)=>{ if(k) (idx[k]=idx[k]||new Set()).add(id); };
  for(const id in PLAYERS){
    const p=PLAYERS[id];
    for(const nm of namesOf(p)){
      add(normalize(nm),id);
      surnameVariants(nm).forEach(v=>add(v,id));
    }
  }
  const o={}; for(const k in idx) o[k]=Array.from(idx[k]);
  return o;
})();

function claimedByOther(g,pid){
  const owners=EXACT_INDEX[g];
  return !!owners && owners.indexOf(pid)===-1;
}

function matchAnswer(guess,pid,base){
  base=(base===undefined)?2:base;
  const p=PLAYERS[pid], g=normalize(guess);
  const out={ok:false,route:"none",dist:null,matched:null,ambiguous:false};
  if(!g) return out;
  const cands=namesOf(p);
  const canon=new Set([p.n.fr,p.n.en,p.n.es].map(normalize));
  for(const c of cands){
    if(g===normalize(c)){
      out.ok=true; out.dist=0; out.matched=c;
      out.route=canon.has(normalize(c))?"exact":"alias";
      return out;
    }
  }
  let best={d:Infinity,c:null};
  for(const c of cands){
    const d=levenshtein(g,normalize(c));
    if(d<best.d) best={d:d,c:c};
  }
  if(best.c && best.d<=thresholdFor(normalize(best.c),base) && !claimedByOther(g,pid)){
    out.ok=true; out.route="fuzzy"; out.dist=best.d; out.matched=best.c; return out;
  }
  const vars=new Set();
  for(const nm of [p.n.en,p.n.fr,p.n.es]) surnameVariants(nm).forEach(v=>vars.add(v));
  let hit=null;
  for(const sn of vars){
    if(!sn) continue;
    const d=levenshtein(g,sn);
    if(d<=thresholdFor(sn,base) && (hit===null||d<hit.d)) hit={sn:sn,d:d};
  }
  if(hit){
    out.dist=hit.d; out.matched=hit.sn; out.route="surname";
    if(hit.d>0 && claimedByOther(g,pid)){ out.ok=false; out.route="none"; return out; }
    if((SURNAME_INDEX[hit.sn]||[]).length>1){ out.ambiguous=true; out.ok=false; }
    else out.ok=true;
    return out;
  }
  out.dist=best.d; out.matched=best.c;
  return out;
}

function mulberry32(seed){
  return function(){
    seed|=0; seed=seed+0x6D2B79F5|0;
    let t=Math.imul(seed^seed>>>15,1|seed);
    t=t+Math.imul(t^t>>>7,61|t)^t;
    return ((t^t>>>14)>>>0)/4294967296;
  };
}
function hashStr(s){let h=2166136261;for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619);}return h>>>0;}
function todayKey(){const d=new Date();return d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0")+"-"+String(d.getDate()).padStart(2,"0");}

const MAX_LINKS=Math.max.apply(null,Object.keys(PLAYERS).map(id=>PLAYERS[id].l||0));
function distractorsFor(pid,rng){
  const tg=PLAYERS[pid];
  const score=id=>{
    const p=PLAYERS[id]; let s=0;
    if(p.pos===tg.pos) s+=3;
    if(p.b&&tg.b&&Math.abs(p.b-tg.b)<=6) s+=2;
    if(p.nat===tg.nat) s+=1;
    // un leurre doit etre aussi connu que la reponse, sinon il ne trompe personne
    s+=2*(p.l||0)/MAX_LINKS;
    s-=1.5*Math.abs((p.l||0)-(tg.l||0))/MAX_LINKS;
    return s;
  };
  return Object.keys(PLAYERS).filter(id=>id!==pid)
    .map(id=>({id:id,s:score(id)+rng()*0.9}))
    .sort((a,b)=>b.s-a.s).slice(0,3).map(o=>o.id);
}
function shuffle(a,rng){const x=a.slice();for(let i=x.length-1;i>0;i--){const j=Math.floor(rng()*(i+1));const t=x[i];x[i]=x[j];x[j]=t;}return x;}

/* ---------- selection des dossiers ---------- */
/* La difficulte tient au mode de reponse, pas seulement a la rarete du transfert.
   Facile : QCM sur les dossiers grand public. Hardcore : saisie libre sur toute la base. */
function poolFor(m){
