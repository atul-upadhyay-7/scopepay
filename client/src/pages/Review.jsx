import React from 'react';
import {useNavigate,Link} from 'react-router-dom';
import {toast} from 'sonner';
import {useScopePay} from '../state.jsx';
import {Eyebrow,Reveal} from '../ui.jsx';
import {Button} from '@/components/ui/button';
import {Card,CardContent,CardDescription,CardHeader,CardTitle} from '@/components/ui/card';
import {Checkbox} from '@/components/ui/checkbox';
import {Label} from '@/components/ui/label';
export default function Review(){
 const {draft,checked,setChecked,busy,aiMessage,confirmReview}=useScopePay();
 const navigate=useNavigate();
 async function go(){
  const r=await confirmReview();
  if(r){toast.success('Review saved for this sandbox demo only.');navigate('/payment');}
  else toast.error('Review was not saved.');
 }
 if(!draft)return <Reveal><Card><CardHeader>
  <Eyebrow>Step 2 - review</Eyebrow>
  <CardTitle>No draft yet</CardTitle>
  <CardDescription>Extract a draft from a brief first. <Link className="link-underline font-semibold text-ember-deep" to="/brief">Go to the brief</Link></CardDescription>
 </CardHeader></Card></Reveal>;
 const monoH3='font-mono text-[11px] font-medium uppercase tracking-[1.8px] text-muted-foreground';
 return <>
  <Reveal><Card>
   <CardHeader>
    <Eyebrow>Step 2 - review the draft</Eyebrow>
    <CardTitle>Check every word against your brief</CardTitle>
    <CardDescription className="max-w-[580px]">The evidence below is copied verbatim from your brief. If it is wrong or missing, the draft is wrong. Open questions are listed, not hidden.</CardDescription>
   </CardHeader>
   <CardContent>
    <h3 className={monoH3}>Summary</h3>
    <p className="mt-2.5 max-w-[620px] text-[14.5px] leading-relaxed text-secondary-foreground">{draft.summary}</p>
    <h3 className={monoH3+' mt-7'}>Deliverables, with source evidence</h3>
    <ul className="mt-1">{draft.deliverables.map((d,i)=>
     <li key={i} className="border-t py-3.5 first:border-t-0 first:pt-2 text-[14.5px] leading-relaxed">{d.description}
      <blockquote className="mb-0.5 mt-2 border-l-2 border-ember bg-background px-3.5 py-2.5 font-mono text-[13px] leading-relaxed text-secondary-foreground">"{d.evidence}"</blockquote>
     </li>)}
    </ul>
    {draft.questions.length>0&&<><h3 className={monoH3+' mt-7'}>Open questions</h3>
     <ul className="mt-2.5 list-disc space-y-1.5 pl-[18px] marker:text-ember text-[14.5px] text-secondary-foreground">{draft.questions.map((q,i)=><li key={i}>{q}</li>)}</ul></>}
    {draft.conflicts.length>0&&<><h3 className={monoH3+' mt-7'}>Conflicts the AI did not resolve</h3>
     <ul className="mt-1">{draft.conflicts.map((c,i)=>
      <li key={i} className="border-t py-3.5 text-[14.5px] leading-relaxed">{c.description}
       {c.evidence.map((e,j)=><blockquote key={j} className="mb-0.5 mt-2 border-l-2 border-ember bg-background px-3.5 py-2.5 font-mono text-[13px] text-secondary-foreground">"{e}"</blockquote>)}
      </li>)}
     </ul></>}
   </CardContent>
  </Card></Reveal>
  <Reveal delay={120}><Card className="card-lift mt-5">
   <CardContent className="pt-6 sm:pt-8">
    <div className="flex items-start gap-3">
     <Checkbox id="ack" checked={checked} onCheckedChange={v=>setChecked(v===true)}/>
     <Label htmlFor="ack" className="cursor-pointer text-sm font-normal leading-relaxed text-secondary-foreground">I checked the summary, deliverables and evidence against the brief, and I understand this is a sandbox demo, not a client contract.</Label>
    </div>
    <Button className="mt-5" disabled={!checked||busy} onClick={go}>{busy?'Saving review...':'Save my review'}</Button>
    {aiMessage&&<p className="mt-4 text-[13px] text-muted-foreground [overflow-wrap:anywhere]">{aiMessage}</p>}
   </CardContent>
  </Card></Reveal>
 </>;
}
