import React,{useEffect,useMemo,useRef} from 'react';
import {Check} from 'lucide-react';
import {cn} from '@/lib/utils';
const STEPS=[['brief','Brief'],['draft','Draft'],['review','Review'],['paid','Payment']];
export function Stepper({done}){
 return <ol className="flex flex-col">{STEPS.map(([key,label],i)=>{
  const isDone=done[key];
  const current=!isDone&&STEPS.slice(0,i).every(([k])=>done[k]);
  return <li key={key} className="relative flex items-center gap-2.5 py-[5px] text-[12.5px] text-muted-foreground">
   {i<STEPS.length-1&&<span className="absolute bottom-[-4px] left-[7px] top-[26px] w-px bg-input"/>}
   <span className={cn('z-[1] inline-flex h-[15px] w-[15px] items-center justify-center rounded-full border bg-sidebar-background font-mono text-[8.5px] transition-all duration-300',
    isDone?'border-ember bg-ember text-primary-foreground':current?'border-ember ring-[3px] ring-accent':'border-input')}>
    {isDone?<Check size={10} strokeWidth={3.5}/>:i+1}</span>
   <span className={cn(isDone&&'text-secondary-foreground',current&&'font-semibold text-accent-foreground')}>{label}</span>
  </li>;})}</ol>;
}
export function Eyebrow({children,className}){return <p className={cn('font-mono text-[10.5px] font-medium uppercase tracking-[2.2px] text-ember',className)}>{children}</p>;}

export function Reveal({children,delay=0,className}){
 return <div className={cn('reveal',className)} style={{'--reveal-delay':delay+'ms'}}>{children}</div>;
}

export function SplitText({text,as:Tag='span',className,wordDelay=70,startDelay=0}){
 return <Tag className={className} aria-label={text}>{text.split(' ').map((w,i)=>
  <span key={i} aria-hidden="true" className="split-word" style={{'--split-delay':(startDelay+i*wordDelay)+'ms'}}>{w}{i<text.split(' ').length-1?'\u00A0':''}</span>)}</Tag>;
}

// Deterministic particle layout so a re-render never reshuffles the field.
function makeParticles(n){
 const out=[];let seed=20261021;
 const rnd=()=>{seed=(seed*1103515245+12345)&0x7fffffff;return seed/0x7fffffff;};
 for(let i=0;i<n;i++)out.push({x:rnd()*100,size:2+rnd()*3.2,op:.22+rnd()*.26,dur:19+rnd()*17,delay:-rnd()*36,drift:(rnd()*10-5)});
 return out;
}
export function Ambient(){
 const particles=useMemo(()=>makeParticles(22),[]);
 return <div aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
  <div className="ambient-aurora"/>
  <div className="ambient-glow ambient-glow-a"/>
  <div className="ambient-glow ambient-glow-b"/>
  {particles.map((p,i)=><span key={i} className="ember-particle" style={{'--p-x':p.x+'%','--p-size':p.size.toFixed(1)+'px','--p-op':p.op.toFixed(2),'--p-dur':p.dur.toFixed(1)+'s','--p-delay':p.delay.toFixed(1)+'s','--p-drift':p.drift.toFixed(1)+'vw'}}/>)}
  <div className="ambient-noise"/>
 </div>;
}

// Count-up number (reactbits Count Up): rAF ease-out, jumps straight to the
// final value under prefers-reduced-motion.
export function CountUp({value,decimals=0,duration=950,className}){
 const ref=useRef(null);
 useEffect(()=>{
  const el=ref.current;if(!el)return;
  if(window.matchMedia('(prefers-reduced-motion: reduce)').matches){el.textContent=value.toFixed(decimals);return;}
  let raf;const start=performance.now();
  const tick=t=>{const p=Math.min(1,(t-start)/duration);const e=1-Math.pow(1-p,3);el.textContent=(value*e).toFixed(decimals);if(p<1)raf=requestAnimationFrame(tick);};
  raf=requestAnimationFrame(tick);
  return ()=>cancelAnimationFrame(raf);
 },[value,decimals,duration]);
 return <span ref={ref} className={className}>{value.toFixed(decimals)}</span>;
}
