import React,{useEffect,useState} from 'react';
import {useScopePay} from '../state.jsx';
import {Eyebrow,Reveal} from '../ui.jsx';
import {Badge} from '@/components/ui/badge';
import {Button} from '@/components/ui/button';
import {Card,CardContent,CardDescription,CardHeader,CardTitle} from '@/components/ui/card';
import {Dialog,DialogContent,DialogDescription,DialogHeader,DialogTitle} from '@/components/ui/dialog';
import {Table,TableBody,TableCell,TableRow} from '@/components/ui/table';
import {Textarea} from '@/components/ui/textarea';
import ScopeGrid from '@/components/ScopeGrid.jsx';
import {Tabs,TabsContent,TabsList,TabsTrigger} from '@/components/ui/tabs';
const EVENT_LABELS={ORDER_CREATED:'Sandbox order created',ORDER_PENDING_REVIEW:'Capture unclear - held for review, never shown as paid',ORDER_COMPLETED:'Payment captured and verified server-side',WEBHOOK_CONFIRMED:'PayPal webhook verified and recorded',WEBHOOK_REJECTED:'A webhook failed verification and was ignored',WEBHOOK_IGNORED:'A verified webhook did not match any order',ORDER_REFUNDED:'Payment refunded in the sandbox, ledger reversed',ORDER_REFUND_FAILED:'Refund answer was unclear - the order stays completed and safe',ORDER_RECOVERED:'Order recovered into a later session with a one-time console code'};
const ACCOUNT_LABELS={paypal_sandbox_clearing:'PayPal sandbox clearing',demo_revenue:'Demo revenue'};
const VERDICT_LABELS={CLAIMED_ADDRESSED:['ok','Claimed addressed'],CLAIMED_PARTIAL:['warn','Claimed partial'],NOT_EVIDENCED:['default','Not evidenced'],UNCLEAR:['warn','Unclear'],EXCLUSION_VIOLATED:['warn','Claimed violation'],EXCLUSION_RESPECT_CLAIMED:['ok','Claimed respected'],EXCLUSION_NO_CLAIM:['outline','No claim either way']};
const RR_LABELS={DRAFT:['default','Awaiting approval'],COMPLETED:['ok','Refunded'],UNCLEAR:['warn','Needs check'],FAILED:['default','Failed']};
const LABELS={DRAFT_REQUIRES_REVIEW:['default','Draft'],REVIEWED_FOR_SANDBOX:['ok','Reviewed'],CREATING:['default','Creating'],CREATED:['default','Order created'],PENDING_REVIEW:['warn','Needs check'],COMPLETED:['ok','Completed'],REFUNDED:['default','Refunded']};
function StateBadge({state}){const [variant,label]=LABELS[state]||['default',state];return <Badge variant={variant}>{label}</Badge>;}
export default function Receipts(){
 const {loadReceipts,requestRecoveryCode,recoverOrder,replayOrder,refundOrder,simulateWebhook,createRefundRequest,approveRefundRequest,config}=useScopePay();
 const [data,setData]=useState(null),[error,setError]=useState('');
 const [selected,setSelected]=useState(null);
 const [selectedOrder,setSelectedOrder]=useState(null);
 const [replayResult,setReplayResult]=useState(null);
 const [replaying,setReplaying]=useState(false);
 const [refunding,setRefunding]=useState(false);
 const [refundResult,setRefundResult]=useState(null);
 const [webhookResult,setWebhookResult]=useState(null);
 const [simulating,setSimulating]=useState(false);
 const [mgText,setMgText]=useState(''),[mgAmount,setMgAmount]=useState(''),[mgReason,setMgReason]=useState('');
 const [mgBusy,setMgBusy]=useState(false),[mgError,setMgError]=useState('');
 const [mgDraft,setMgDraft]=useState(null),[mgResult,setMgResult]=useState(null);
 const [tab,setTab]=useState('drafts'),[recId,setRecId]=useState(''),[recCode,setRecCode]=useState(''),[recStep,setRecStep]=useState('id'),[recBusy,setRecBusy]=useState(false),[recMsg,setRecMsg]=useState(null);
 async function mgCreate(){if(!selectedOrder||mgBusy)return;setMgBusy(true);setMgError('');setMgResult(null);
  try{const r=await createRefundRequest(selectedOrder.orderId,{amount:mgAmount.trim(),reason:mgReason.trim(),deliveryText:mgText});
   if(r.status===201&&r.data.refundRequestId){setMgDraft(r.data);const fresh=await loadReceipts();setData(fresh);}
   else setMgError(`The server refused the request: ${r.data.error||('HTTP '+r.status)}. Nothing was created.`);
  }catch(e){setMgError(`Request failed: ${e.message}. Nothing was created.`);}
  finally{setMgBusy(false);}}
 async function mgApprove(){if(!mgDraft||mgBusy||mgDraft.state!=='DRAFT')return;setMgBusy(true);setMgError('');setMgResult(null);
  try{const r=await approveRefundRequest(mgDraft.refundRequestId);
   const fresh=await loadReceipts();setData(fresh);
   const updated=fresh.orders.find(x=>x.requestId===selectedOrder.requestId);if(updated)setSelectedOrder(updated);
   if(r.status===200&&r.data.state==='COMPLETED'){setMgResult({ok:true,text:`Make-good refund completed in the sandbox (refund ${r.data.paypalRefundId}, ${r.data.amount} ${r.data.currency}). The ledger below shows the partial reversal.`});setMgDraft(null);setMgText('');setMgAmount('');setMgReason('');}
   else if(r.status===409&&r.data.error==='REFUND_NOT_COMPLETED'){setMgDraft(r.data);setMgResult({ok:false,text:'PayPal did not confirm the refund (it may be pending). The request is held as Needs check; the reconciler re-reads PayPal and only PayPal\u2019s own record can complete it.'});}
   else setMgResult({ok:false,text:`Refund answer was unclear (HTTP ${r.status}: ${r.data.error||'unknown'}). Nothing was treated as refunded.`});
  }catch(e){setMgResult({ok:false,text:`Approval failed: ${e.message}. Nothing was treated as refunded.`});}
  finally{setMgBusy(false);}}
 function openOrder(o){setSelectedOrder(o);setMgDraft(null);setMgError('');setMgResult(null);setMgText('');setMgAmount('');setMgReason('');}
 async function printCode(){if(recBusy||recId.trim().length<5)return;setRecBusy(true);setRecMsg(null);
  try{const r=await requestRecoveryCode(recId.trim());
   if(r.status===200){setRecStep('code');setRecMsg({ok:true,text:'A one-time code was printed in the backend terminal window (valid 5 minutes, one use). Enter it below.'});}
   else if(r.status===404)setRecMsg({ok:false,text:'No order with that ID exists in this app\u2019s records on this machine. Nothing changed.'});
   else if(r.status===400)setRecMsg({ok:false,text:'That does not look like a PayPal order ID (capital letters and digits only).'});
   else setRecMsg({ok:false,text:`Could not print a code (HTTP ${r.status}): ${r.data.error||'unknown'}. Nothing changed.`});
  }catch(e){setRecMsg({ok:false,text:`Could not print a code: ${e.message}. Nothing changed.`});}
  finally{setRecBusy(false);}}
 async function recover(){if(recBusy||recCode.trim().length<8)return;setRecBusy(true);setRecMsg(null);
  try{const id=recId.trim();const r=await recoverOrder(id,recCode.trim());
   if(r.status===200&&r.data.recovered){const fresh=await loadReceipts();setData(fresh);setTab('orders');const found=fresh.orders.find(x=>x.orderId===id);if(found)openOrder(found);setRecMsg({ok:true,text:'Order recovered into this session\u2019s record. It stays here across closed tabs from now on.'});setRecId('');setRecCode('');setRecStep('id');}
   else if(r.status===403)setRecMsg({ok:false,text:'The code did not match, expired, or was already used. Nothing changed - print a fresh code and try again.'});
   else if(r.status===404)setRecMsg({ok:false,text:'No order with that ID exists in this app\u2019s records on this machine. Nothing changed.'});
   else setRecMsg({ok:false,text:`Recovery failed (HTTP ${r.status}): ${r.data.error||'unknown'}. Nothing changed.`});
  }catch(e){setRecMsg({ok:false,text:`Recovery failed: ${e.message}. Nothing changed.`});}
  finally{setRecBusy(false);}}
 async function simulate(){if(!selectedOrder||simulating)return;setSimulating(true);setWebhookResult(null);
  try{const r=await simulateWebhook(selectedOrder.requestId);
   const fresh=await loadReceipts();const updated=fresh.orders.find(x=>x.requestId===selectedOrder.requestId);if(updated)setSelectedOrder(updated);
   if(r.data.status==='WEBHOOK_CONFIRMED')setWebhookResult({ok:true,text:'The simulated webhook passed PayPal verification (demo stub) and was recorded on this order. See the timeline.'});
   else if(r.data.status==='WEBHOOK_REJECTED')setWebhookResult({ok:false,text:'The simulated webhook failed verification and was ignored. That is the safe behavior: with real sandbox credentials an unsigned webhook is always rejected.'});
   else setWebhookResult({ok:false,text:`Webhook simulation answered: ${r.data.status||r.data.error||('HTTP '+r.status)}. Nothing about the order changed.`});
  }catch(e){setWebhookResult({ok:false,text:`Simulation failed: ${e.message}. Nothing about the order changed.`});}
  finally{setSimulating(false);}}
 async function refund(){if(!selectedOrder||refunding)return;setRefunding(true);setRefundResult(null);
  try{const r=await refundOrder(selectedOrder);
   if(r.status===200&&r.data.status==='REFUNDED'){
    const fresh=await loadReceipts();const updated=fresh.orders.find(x=>x.requestId===selectedOrder.requestId);if(updated)setSelectedOrder(updated);
    setRefundResult({ok:true,text:`Refunded in the sandbox (refund ${r.data.refundId}). The ledger below now shows the reversal, and the state machine moved this order to Refunded. A second refund click returns the same receipt, never a second refund.`});
   }else if(r.status===409){setRefundResult({ok:false,text:`The server refused the refund: ${r.data.error||'ORDER_NOT_COMPLETED'}. Only a completed sandbox payment can be refunded.`});}
   else{setRefundResult({ok:false,text:`Refund answer was unclear (HTTP ${r.status}: ${r.data.error||'unknown'}). The order stays completed; nothing was assumed.`});}
  }catch(e){setRefundResult({ok:false,text:`Refund failed: ${e.message}. The order was not treated as refunded.`});}
  finally{setRefunding(false);}}
 async function replay(){if(!selectedOrder||replaying)return;setReplaying(true);setReplayResult(null);
  try{const r=await replayOrder(selectedOrder);
   if(r.status===200&&r.data.orderId&&r.data.orderId===selectedOrder.orderId){
    const fresh=await loadReceipts();const updated=fresh.orders.find(x=>x.requestId===selectedOrder.requestId);if(updated)setSelectedOrder(updated);
    const created=(updated?.timeline||[]).filter(e=>e.type==='ORDER_CREATED').length;
    setReplayResult({ok:true,text:`Replayed with the same idempotency key: the server returned the original order (HTTP 200), not a new one. The timeline still shows ${created} "order created" event - no duplicate PayPal order, no double charge.`});
   }else if(r.status===409){setReplayResult({ok:false,text:`The server held the replay: ${r.data.error||'CREATE_PENDING_REVIEW'}. The original reservation is untouched.`});}
   else{setReplayResult({ok:false,text:`Unexpected answer (HTTP ${r.status}): ${r.data.error||'unknown'}. Nothing was treated as paid.`});}
  }catch(e){setReplayResult({ok:false,text:`Replay failed: ${e.message}. Nothing was treated as paid.`});}
  finally{setReplaying(false);}}
 useEffect(()=>{loadReceipts().then(setData).catch(e=>setError(e.message));},[]);
 if(error)return <Card><CardHeader><Eyebrow>Receipts</Eyebrow><CardTitle>Receipts unavailable</CardTitle><CardDescription>{error}</CardDescription></CardHeader></Card>;
 if(!data)return <Card><CardHeader><Eyebrow>Receipts</Eyebrow><CardTitle>Loading...</CardTitle></CardHeader></Card>;
 return <>
  <Reveal><Eyebrow>Your session's record</Eyebrow>
  <h2 className="mt-3 font-serif text-2xl font-semibold tracking-tight">Receipts</h2></Reveal>
  {config?.orderRecovery&&<Reveal delay={50}><Card className="mt-5"><CardContent className="pt-4">
   <span className="text-xs text-muted-foreground">Recover an earlier order</span>
   <p className="mt-1 text-xs leading-relaxed text-muted-foreground">Receipts now survive closed tabs and restarts in this browser. Orders made before this fix belonged to a closed tab. Paste the PayPal order ID (capital letters and digits) and the server prints a one-time code in the backend terminal - only someone who can read that terminal can complete the recovery.</p>
   <div className="mt-2 flex flex-wrap gap-2">
    <input className="min-w-0 flex-1 rounded-md border border-input bg-background px-2.5 py-1.5 font-mono text-xs" placeholder="PayPal order ID" maxLength={40} value={recId} onChange={e=>setRecId(e.target.value)}/>
    <Button variant="outline" size="sm" disabled={recBusy||recId.trim().length<5} onClick={printCode}>{recBusy&&recStep==='id'?'Printing...':'Print recovery code'}</Button>
   </div>
   {recStep==='code'&&<div className="mt-2 flex flex-wrap gap-2">
    <input className="w-32 rounded-md border border-input bg-background px-2.5 py-1.5 font-mono text-xs tracking-widest" placeholder="XXXX-XXXX" maxLength={9} value={recCode} onChange={e=>setRecCode(e.target.value)}/>
    <Button variant="outline" size="sm" disabled={recBusy||recCode.trim().length<8} onClick={recover}>{recBusy?'Recovering...':'Recover order'}</Button>
   </div>}
   {recMsg&&<p className={`mt-2 rounded-md border px-3 py-2 text-xs ${recMsg.ok?'border-primary/40 bg-accent text-accent-foreground':'border-destructive/40 bg-destructive/10 text-foreground'}`}>{recMsg.text}</p>}
  </CardContent></Card></Reveal>}
  <Reveal delay={100}><Tabs value={tab} onValueChange={setTab} className="mt-5">
   <TabsList><TabsTrigger value="drafts">Drafts and reviews</TabsTrigger><TabsTrigger value="orders">Orders and captures</TabsTrigger></TabsList>
   <TabsContent value="drafts">
    {data.drafts.length===0?<Card><CardContent className="pt-6 text-sm text-muted-foreground">No drafts in this session yet.</CardContent></Card>:
    <Card><CardContent className="pt-4">
     <ScopeGrid rowData={data.drafts} onRowClicked={e=>setSelected(e.data)} columnDefs={[
      {headerName:'Scope',field:'scopeId',flex:0.7,minWidth:100,cellRenderer:p=><span className="font-mono text-xs">{p.value.slice(0,8)}...</span>},
      {headerName:'Summary',field:'summary',flex:1.8,minWidth:220},
      {headerName:'State',field:'state',flex:0.7,minWidth:110,cellRenderer:p=><span className="flex h-full items-center"><StateBadge state={p.value}/></span>,filter:true},
      {headerName:'Reviewed',field:'reviewedAt',flex:1,minWidth:150,valueFormatter:p=>p.value?new Date(p.value).toLocaleString():'-'}
     ]}/>
    </CardContent></Card>}
   </TabsContent>
   <TabsContent value="orders">
    {data.orders.length===0?<Card><CardContent className="pt-6 text-sm text-muted-foreground">No orders in this session yet.</CardContent></Card>:
    <Card><CardContent className="pt-4">
     <ScopeGrid rowData={data.orders} onRowClicked={e=>openOrder(e.data)} columnDefs={[
      {headerName:'Order',field:'orderId',flex:0.9,minWidth:120,cellRenderer:p=><span className="font-mono text-xs">{p.value||'(creating)'}</span>},
      {headerName:'State',field:'state',flex:0.7,minWidth:110,cellRenderer:p=><span className="flex h-full items-center"><StateBadge state={p.value}/></span>,filter:true},
      {headerName:'Capture',field:'captureId',flex:0.9,minWidth:110,cellRenderer:p=><span className="font-mono text-xs">{p.value||'-'}</span>},
      {headerName:'Completed',field:'completedAt',flex:1,minWidth:150,valueFormatter:p=>p.value?new Date(p.value).toLocaleString():'-'}
     ]}/>
    </CardContent></Card>}
   </TabsContent>
  </Tabs></Reveal>
  <Dialog open={!!selected} onOpenChange={open=>!open&&setSelected(null)}>
   {selected&&<DialogContent className="max-h-[85vh] overflow-y-auto">
    <DialogHeader>
     <DialogTitle>Scope {selected.scopeId.slice(0,8)}...</DialogTitle>
     <DialogDescription>Saved under this browser's demo identity.</DialogDescription>
    </DialogHeader>
    <div className="space-y-3 text-sm">
     <div className="flex items-center justify-between gap-3"><span className="text-muted-foreground">State</span><StateBadge state={selected.state}/></div>
     <div><span className="text-muted-foreground">Full scope id</span><p className="mt-1 break-all font-mono text-xs">{selected.scopeId}</p></div>
     <div><span className="text-muted-foreground">Summary</span><p className="mt-1 leading-relaxed text-secondary-foreground">{selected.summary}</p></div>
     {selected.deliverables&&selected.deliverables.length>0&&<div>
      <span className="text-muted-foreground">Deliverables, with source evidence</span>
      <ul className="mt-2 space-y-3">{selected.deliverables.map((d,i)=><li key={i}>
       <p className="font-medium leading-snug text-secondary-foreground">{d.description}</p>
       <blockquote className="mb-0.5 mt-1 border-l-2 border-ember bg-background px-3 py-2 font-mono text-[12px] leading-relaxed text-secondary-foreground">"{d.evidence}"</blockquote>
      </li>)}</ul>
     </div>}
     {selected.questions&&selected.questions.length>0&&<div>
      <span className="text-muted-foreground">Open questions</span>
      <ul className="mt-1.5 list-disc space-y-1 pl-5 text-secondary-foreground">{selected.questions.map((q,i)=><li key={i}>{q}</li>)}</ul>
     </div>}
     {selected.conflicts&&selected.conflicts.length>0&&<div>
      <span className="text-muted-foreground">Conflicts</span>
      <ul className="mt-1.5 space-y-2">{selected.conflicts.map((c,i)=><li key={i}>
       <p className="font-medium leading-snug text-secondary-foreground">{c.description}</p>
       {(c.evidence||[]).map((e,j)=><blockquote key={j} className="mb-0.5 mt-1 border-l-2 border-ember bg-background px-3 py-2 font-mono text-[12px] leading-relaxed text-secondary-foreground">"{e}"</blockquote>)}
      </li>)}</ul>
     </div>}
     <div className="flex justify-between gap-3"><span className="text-muted-foreground">Reviewed</span><span className="tabular-nums">{selected.reviewedAt?new Date(selected.reviewedAt).toLocaleString():'-'}</span></div>
    </div>
   </DialogContent>}
  </Dialog>
  <Dialog open={!!selectedOrder} onOpenChange={open=>!open&&setSelectedOrder(null)}>
   {selectedOrder&&<DialogContent className="max-h-[85vh] overflow-y-auto">
    <DialogHeader>
     <DialogTitle>Order {selectedOrder.orderId||'(creating)'}</DialogTitle>
     <DialogDescription>Every step the server recorded for this sandbox order.</DialogDescription>
    </DialogHeader>
    <div className="space-y-4 text-sm">
     <div className="flex items-center justify-between gap-3"><span className="text-muted-foreground">State</span><StateBadge state={selectedOrder.state}/></div>
     <div>
      <span className="text-muted-foreground">Payment timeline</span>
      {(!selectedOrder.timeline||selectedOrder.timeline.length===0)?
       <p className="mt-1 text-muted-foreground">No server events recorded yet.</p>:
       <ol className="mt-2 space-y-2 border-l-2 border-border pl-3">
        {selectedOrder.timeline.map((e,i)=><li key={i} className="reveal" style={{'--reveal-delay':(i*80)+'ms'}}>
         <div className="font-medium">{EVENT_LABELS[e.type]||e.type}</div>
         <div className="font-mono text-xs text-muted-foreground">{new Date(e.at).toLocaleString()}</div>
         {e.type==='ORDER_COMPLETED'&&<div className="mt-0.5 font-mono text-xs text-muted-foreground">capture {e.payload.captureId} - {e.payload.amount} {e.payload.currency}</div>}
         {e.type==='ORDER_REFUNDED'&&<div className="mt-0.5 font-mono text-xs text-muted-foreground">refund {e.payload.refundId} - {e.payload.amount} {e.payload.currency} reversed</div>}
         {e.type==='ORDER_PENDING_REVIEW'&&<div className="mt-0.5 text-xs text-muted-foreground">An unclear PayPal answer never becomes "paid". A person must check it.</div>}
        </li>)}
       </ol>}
     </div>
     {selectedOrder.ledger&&selectedOrder.ledger.length>0&&<div>
      <span className="text-muted-foreground">Ledger (double-entry, must net to zero)</span>
      <div className="mt-2 overflow-hidden rounded-md border border-border">
       <Table>
        <TableBody>
         {selectedOrder.ledger.map((e,i)=><TableRow key={i}>
          <TableCell className="py-2">{ACCOUNT_LABELS[e.account]||e.account}</TableCell>
          <TableCell className="py-2"><Badge variant={e.direction==='DEBIT'?'default':'ok'}>{e.direction}</Badge></TableCell>
          <TableCell className="py-2 text-right font-mono text-xs tabular-nums">{e.direction==='DEBIT'?'+':'-'}{Number(e.amount).toFixed(2)} {e.currency}</TableCell>
         </TableRow>)}
        </TableBody>
       </Table>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">The server posts both sides in one transaction and rejects the write if the group does not net to zero.</p>
     </div>}
     <div className="flex justify-between gap-3"><span className="text-muted-foreground">Request id</span><span className="break-all font-mono text-xs">{selectedOrder.requestId}</span></div>
     {selectedOrder.orderId&&<div className="border-t border-border pt-3">
      <div className="flex flex-wrap gap-2">
       <Button variant="outline" size="sm" onClick={replay} disabled={replaying}>{replaying?'Replaying...':'Replay this request'}</Button>
       {selectedOrder.state==='COMPLETED'&&!(data.refundRequests||[]).some(r=>r.orderRequestId===selectedOrder.requestId&&!['FAILED'].includes(r.state))&&<Button variant="outline" size="sm" onClick={refund} disabled={refunding}>{refunding?'Refunding...':'Refund this payment'}</Button>}
       {config?.demoTools&&selectedOrder.captureId&&<Button variant="outline" size="sm" onClick={simulate} disabled={simulating}>{simulating?'Simulating...':'Simulate webhook'}</Button>}
      </div>
      <p className="mt-1.5 text-xs text-muted-foreground">Replay sends the same idempotency key again to prove a retry cannot double-charge.{config?.demoTools&&selectedOrder.captureId?' Simulate webhook sends a synthetic PayPal webhook through the real verification path.':''}</p>
      {replayResult&&<p className={`mt-2 rounded-md border px-3 py-2 text-xs ${replayResult.ok?'border-primary/40 bg-accent text-accent-foreground':'border-destructive/40 bg-destructive/10 text-foreground'}`}>{replayResult.text}</p>}
      {refundResult&&<p className={`mt-2 rounded-md border px-3 py-2 text-xs ${refundResult.ok?'border-primary/40 bg-accent text-accent-foreground':'border-destructive/40 bg-destructive/10 text-foreground'}`}>{refundResult.text}</p>}
      {webhookResult&&<p className={`mt-2 rounded-md border px-3 py-2 text-xs ${webhookResult.ok?'border-primary/40 bg-accent text-accent-foreground':'border-destructive/40 bg-destructive/10 text-foreground'}`}>{webhookResult.text}</p>}
     </div>}
     {selectedOrder.state==='COMPLETED'&&selectedOrder.scopeId&&<div className="border-t border-border pt-3">
      <span className="text-muted-foreground">Make-good refund, with evidence comparison</span>
      <p className="mt-1 text-xs text-muted-foreground">If the client reports missing work, paste the delivery account below. The AI compares it with the agreed scope and cites what it found. The comparison reflects only what the account claims - it never verifies actual delivery, links in the account are not opened, and the AI never sets an amount. You decide whether to refund and how much.</p>
      {(data.refundRequests||[]).filter(r=>r.orderRequestId===selectedOrder.requestId).length>0&&<ul className="mt-2 space-y-1.5">
       {(data.refundRequests||[]).filter(r=>r.orderRequestId===selectedOrder.requestId).map(r=>{const[v,label]=RR_LABELS[r.state]||['default',r.state];return <li key={r.refundRequestId} className="flex items-center justify-between gap-2 rounded-md border border-border px-2.5 py-1.5 text-xs">
        <span className="tabular-nums">{r.amount} {r.currency}</span><Badge variant={v}>{label}</Badge>
        <span className="font-mono text-muted-foreground">{r.paypalRefundId||'-'}</span></li>;})}
      </ul>}
      {!mgDraft&&<>
       <Textarea className="mt-2" maxLength={6000} placeholder="What was delivered, in your own words (self-reported account)" value={mgText} onChange={e=>setMgText(e.target.value)}/>
       <div className="mt-2 flex flex-wrap gap-2">
        <input className="w-24 rounded-md border border-input bg-background px-2.5 py-1.5 font-mono text-xs tabular-nums" placeholder="0.00" value={mgAmount} onChange={e=>setMgAmount(e.target.value)}/>
        <input className="min-w-0 flex-1 rounded-md border border-input bg-background px-2.5 py-1.5 text-xs" placeholder="Reason (e.g. client reports missing work)" maxLength={500} value={mgReason} onChange={e=>setMgReason(e.target.value)}/>
        <Button variant="outline" size="sm" disabled={mgBusy||mgText.trim().length<20||!mgAmount.trim()||!mgReason.trim()} onClick={mgCreate}>{mgBusy?'Comparing...':'Compare and prepare refund'}</Button>
       </div>
      </>}
      {mgDraft&&<div className="mt-2 rounded-md border border-border p-2.5">
       <span className="text-xs text-muted-foreground">Comparison - claim coverage only, not verified delivery</span>
       {config?.aiMode==='fixture'&&<div className="mt-1.5"><Badge variant="warn">Fixture replay - synthetic demo output, not a live AI run</Badge></div>}
       <ul className="mt-2 space-y-2.5">{mgDraft.comparison.comparisons.map((c,i)=>{const[v,label]=VERDICT_LABELS[c.verdict]||['default',c.verdict];return <li key={i}>
        <div className="flex items-center justify-between gap-2"><span className="text-xs font-medium">{c.deliverable}</span><span className="flex items-center gap-1">{c.verdict.startsWith('EXCLUSION_')&&<Badge variant="outline">scope exclusion</Badge>}<Badge variant={v}>{label}</Badge></span></div>
        {c.quote&&<blockquote className="mb-0.5 mt-1 border-l-2 border-ember bg-background px-3 py-2 font-mono text-[12px] leading-relaxed text-secondary-foreground">"{c.quote}"</blockquote>}
        <p className="mt-0.5 text-xs text-muted-foreground">{c.note}</p>
       </li>;})}</ul>
       {mgDraft.comparison.complaintCoverage&&<div className="mt-2.5 border-t border-border pt-2.5">
        <span className="text-xs text-muted-foreground">Client complaint coverage - the complaint is the client&#39;s unverified claim, not evidence</span>
        {mgDraft.comparison.complaintCoverage.length===0
         ?<p className="mt-1 text-xs text-muted-foreground">The complaint does not explicitly mention any scoped deliverable. Absence means not mentioned, never agreement.</p>
         :<ul className="mt-2 space-y-2">{mgDraft.comparison.complaintCoverage.map((cc,i)=><li key={i}>
           <div className="flex items-center justify-between gap-2"><span className="text-xs font-medium">{cc.deliverable}</span><Badge variant="outline">{cc.claim==='EXPLICITLY_DISPUTED'?'disputed by client':'mentioned by client'}</Badge></div>
           <blockquote className="mb-0.5 mt-1 border-l-2 border-ember bg-background px-3 py-2 font-mono text-[12px] leading-relaxed text-secondary-foreground">&quot;{cc.quote}&quot;</blockquote>
          </li>)}</ul>}
       </div>}
       <p className="mt-2 text-xs text-muted-foreground">{mgDraft.comparison.overallNote}</p>
       <div className="mt-2.5 flex items-center justify-between gap-2 border-t border-border pt-2.5">
        {(!mgDraft.state||mgDraft.state==='DRAFT')&&<>
         <span className="text-xs">Refund <span className="font-mono font-semibold tabular-nums">{mgDraft.amount} {mgDraft.currency}</span> to the buyer?</span>
         <Button size="sm" disabled={mgBusy} onClick={mgApprove}>{mgBusy?'Refunding...':'Approve this refund'}</Button>
        </>}
        {mgDraft.state==='UNCLEAR'&&<span className="text-xs text-muted-foreground">Held as Needs check - the reconciler re-reads PayPal automatically, and only PayPal's own record can complete it. There is nothing to approve again.</span>}
        {mgDraft.state==='COMPLETED'&&<span className="text-xs text-muted-foreground">Refunded in the sandbox. There is nothing to approve again.</span>}
        {mgDraft.state==='FAILED'&&<span className="text-xs text-muted-foreground">PayPal marked this refund failed; nothing was refunded. Start a fresh comparison if you want to try again.</span>}
       </div>
      </div>}
      {mgError&&<p className="mt-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-foreground">{mgError}</p>}
      {mgResult&&<p className={`mt-2 rounded-md border px-3 py-2 text-xs ${mgResult.ok?'border-primary/40 bg-accent text-accent-foreground':'border-destructive/40 bg-destructive/10 text-foreground'}`}>{mgResult.text}</p>}
     </div>}
    </div>
   </DialogContent>}
  </Dialog>
 </>;
}
