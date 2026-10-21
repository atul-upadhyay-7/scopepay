import React from 'react';
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
    isDone?'border-green bg-green text-primary-foreground':current?'border-green ring-[3px] ring-accent':'border-input')}>
    {isDone?<Check size={10} strokeWidth={3.5}/>:i+1}</span>
   <span className={cn(isDone&&'text-secondary-foreground',current&&'font-semibold text-accent-foreground')}>{label}</span>
  </li>;})}</ol>;
}
export function Eyebrow({children,className}){return <p className={cn('font-mono text-[10.5px] font-medium uppercase tracking-[2.2px] text-green',className)}>{children}</p>;}

export function Reveal({children,delay=0,className}){
 return <div className={cn('reveal',className)} style={{'--reveal-delay':delay+'ms'}}>{children}</div>;
}
