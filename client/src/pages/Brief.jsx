import React from 'react';
import {useNavigate} from 'react-router-dom';
import {toast} from 'sonner';
import {useScopePay,EXAMPLE} from '../state.jsx';
import {Eyebrow,Reveal} from '../ui.jsx';
import {Button} from '@/components/ui/button';
import {Card,CardContent,CardDescription,CardHeader,CardTitle} from '@/components/ui/card';
import {Label} from '@/components/ui/label';
import {Textarea} from '@/components/ui/textarea';
export default function Brief(){
 const {brief,setBrief,busy,aiMessage,extract}=useScopePay();
 const navigate=useNavigate();
 async function go(){
  const draft=await extract();
  if(draft){toast.success('Draft ready for review. Nothing is approved or charged.');navigate('/review');}
  else toast.error('No draft was accepted. The AI is not replaced by a canned result.');
 }
 return <Reveal><Card>
  <CardHeader>
   <Eyebrow>Step 1 - the brief</Eyebrow>
   <CardTitle>A synthetic brief, nothing real</CardTitle>
   <CardDescription className="max-w-[580px]">Paste a practice brief. The AI reads it as untrusted data: it drafts a scope and must copy evidence word for word. It cannot set a price or approve anything.</CardDescription>
  </CardHeader>
  <CardContent>
   <Label htmlFor="brief">Client brief <span className="font-normal text-muted-foreground">({brief.length}/6000 characters, synthetic only)</span></Label>
   <Textarea id="brief" className="mt-2.5" maxLength={6000} value={brief} onChange={e=>setBrief(e.target.value)}/>
   <div className="mt-4 flex flex-wrap gap-2.5">
    <Button disabled={busy||brief.trim().length<20} onClick={go}>{busy?'Extracting...':'Extract a draft'}</Button>
    <Button variant="outline" disabled={busy} onClick={()=>setBrief(EXAMPLE)}>Reset example</Button>
   </div>
   {aiMessage&&<p className="mt-4 text-[13px] text-muted-foreground [overflow-wrap:anywhere]">{aiMessage}</p>}
  </CardContent>
 </Card></Reveal>;
}
