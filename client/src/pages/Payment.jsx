import React,{useState} from 'react';
import {Link} from 'react-router-dom';
import {useScopePay} from '../state.jsx';
import {Eyebrow,Reveal,CountUp} from '../ui.jsx';
import {CheckCircle2} from 'lucide-react';
import {Button} from '@/components/ui/button';
import {Card,CardContent,CardDescription,CardHeader,CardTitle} from '@/components/ui/card';
import {Separator} from '@/components/ui/separator';
function Row({label,children}){
 return <div className="flex items-baseline justify-between gap-4 border-b py-2.5 text-[13.5px] last:border-0">
  <span className="text-muted-foreground">{label}</span>
  <span className="text-right font-medium">{children}</span>
 </div>;
}
export default function Payment(){
 const {config,review,ready,setupIssue,checkoutClosed,message,paid,checkout,setFailureMode}=useScopePay();
 const [failureMode,setMode]=useState('off');
 const [modeBusy,setModeBusy]=useState(false);
 const MODES=[['off','Off'],['capture-pending','Capture stays unclear'],['capture-wrong-amount','Capture returns wrong amount'],['create-down','Order create fails']];
 async function pick(m){if(modeBusy)return;setModeBusy(true);try{await setFailureMode(m);setMode(m);}catch(e){}finally{setModeBusy(false);}}
 if(!review)return <Reveal><Card><CardHeader>
  <Eyebrow>Step 3 - payment</Eyebrow>
  <CardTitle>Review required first</CardTitle>
  <CardDescription>Every sandbox order needs your saved review. No review, no payment button. <Link className="link-underline font-semibold text-ember-deep" to="/review">Go to review</Link></CardDescription>
 </CardHeader></Card></Reveal>;
 return <>
  <Reveal><Card>
   <CardHeader className="relative">
    <Eyebrow>Step 3 - sandbox payment</Eyebrow>
    <CardTitle>One fixed test item</CardTitle>
    <div className="mt-4 font-serif text-[30px] font-medium tracking-tight tabular-nums sm:absolute sm:right-8 sm:top-8 sm:mt-0 sm:text-right">$<CountUp value={10} decimals={2}/>
     <span className="ml-2 font-mono text-[10px] font-medium uppercase tracking-[1.5px] text-muted-foreground sm:ml-0 sm:mt-0.5 sm:block">USD, sandbox</span>
    </div>
   </CardHeader>
   <CardContent>
    <Separator className="mb-2"/>
    <Row label="Item">Scope review demo ({config?.sku||'sandbox-demo'})</Row>
    <Row label="Price owner">The server. Never the AI, never this page.</Row>
    <Row label="Review"><span className="font-semibold text-ember-deep">Saved for scope {review.scopeId.slice(0,8)}...</span></Row>
    <Row label="Mode">PayPal sandbox. No real money.</Row>
    <div className="mt-5">
     {paid?<div className="success-pop flex items-start gap-3 rounded-md border border-primary/40 bg-accent px-4 py-3.5">
       <span className="success-ring mt-0.5 inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-ember text-primary-foreground"><CheckCircle2 size={13} strokeWidth={3}/></span>
       <p className="font-semibold text-ember-deep">Sandbox payment completed. Capture <span className="font-mono text-[13px]">{paid.captureId}</span>. See it on the Receipts page.</p>
      </div>
      :<Button disabled={!ready} onClick={checkout}>{setupIssue?'Sandbox setup needed':checkoutClosed?'Checkout closed - reload to start again':ready?'Pay with PayPal (sandbox)':'Preparing sandbox checkout...'}</Button>}
     {message&&<p className="mt-4 text-[13px] text-muted-foreground [overflow-wrap:anywhere]">{message}</p>}
    </div>
   </CardContent>
  </Card></Reveal>
  {config?.demoTools&&<Reveal delay={120}><Card className="card-lift mt-5">
   <CardHeader>
    <Eyebrow>Demo controls</Eyebrow>
    <CardDescription className="max-w-[620px] text-[14.5px]">Simulated PayPal failures. The scripted stand-in misbehaves; the server's real safety code - pending review, amount checks, idempotent reservations - handles every case. Nothing here can mark a payment as paid.</CardDescription>
   </CardHeader>
   <CardContent>
    <div className="flex flex-wrap gap-2">
     {MODES.map(([value,label])=><Button key={value} variant={failureMode===value?'default':'outline'} size="sm" disabled={modeBusy} onClick={()=>pick(value)}>{label}</Button>)}
    </div>
    {failureMode!=='off'&&<p className="mt-3 rounded-md border border-primary/40 bg-accent px-3 py-2 text-xs text-accent-foreground">Failure simulation active: {MODES.find(([v])=>v===failureMode)[1]}. Start a checkout, then open the order on the Receipts page to see how the server handled it. Switch back to Off when done.</p>}
   </CardContent>
  </Card></Reveal>}
  <Reveal delay={config?.demoTools?240:120}><Card className="card-lift mt-5">
   <CardHeader>
    <Eyebrow>What happens</Eyebrow>
    <CardDescription className="max-w-[620px] text-[14.5px]">PayPal opens its own sandbox checkout. You approve there as a test buyer. The server then captures the payment itself, checks the amount is exactly USD 10.00, and writes the receipt. An unclear answer from PayPal never becomes "paid".</CardDescription>
   </CardHeader>
  </Card></Reveal>
 </>;
}
