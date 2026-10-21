import React,{useEffect,useState} from 'react';
import {useScopePay} from '../state.jsx';
import {Eyebrow,Reveal} from '../ui.jsx';
import {Badge} from '@/components/ui/badge';
import {Button} from '@/components/ui/button';
import {Card,CardContent,CardDescription,CardHeader,CardTitle} from '@/components/ui/card';
import {Dialog,DialogContent,DialogDescription,DialogHeader,DialogTitle} from '@/components/ui/dialog';
import {Table,TableBody,TableCell,TableRow} from '@/components/ui/table';
import ScopeGrid from '@/components/ScopeGrid.jsx';
import {Tabs,TabsContent,TabsList,TabsTrigger} from '@/components/ui/tabs';
const EVENT_LABELS={ORDER_CREATED:'Sandbox order created',ORDER_PENDING_REVIEW:'Capture unclear - held for review, never shown as paid',ORDER_COMPLETED:'Payment captured and verified server-side',WEBHOOK_CONFIRMED:'PayPal webhook verified and recorded',WEBHOOK_REJECTED:'A webhook failed verification and was ignored',WEBHOOK_IGNORED:'A verified webhook did not match any order',ORDER_REFUNDED:'Payment refunded in the sandbox, ledger reversed',ORDER_REFUND_FAILED:'Refund answer was unclear - the order stays completed and safe'};
const ACCOUNT_LABELS={paypal_sandbox_clearing:'PayPal sandbox clearing',demo_revenue:'Demo revenue'};
const LABELS={DRAFT_REQUIRES_REVIEW:['default','Draft'],REVIEWED_FOR_SANDBOX:['ok','Reviewed'],CREATING:['default','Creating'],CREATED:['default','Order created'],PENDING_REVIEW:['warn','Needs check'],COMPLETED:['ok','Completed'],REFUNDED:['default','Refunded']};
function StateBadge({state}){const [variant,label]=LABELS[state]||['default',state];return <Badge variant={variant}>{label}</Badge>;}
export default function Receipts(){
 const {loadReceipts,replayOrder,refundOrder,simulateWebhook,config}=useScopePay();
 const [data,setData]=useState(null),[error,setError]=useState('');
 const [selected,setSelected]=useState(null);
 const [selectedOrder,setSelectedOrder]=useState(null);
 const [replayResult,setReplayResult]=useState(null);
 const [replaying,setReplaying]=useState(false);
 const [refunding,setRefunding]=useState(false);
 const [refundResult,setRefundResult]=useState(null);
 const [webhookResult,setWebhookResult]=useState(null);
 const [simulating,setSimulating]=useState(false);
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
  <Reveal delay={100}><Tabs defaultValue="drafts" className="mt-5">
   <TabsList><TabsTrigger value="drafts">Drafts and reviews</TabsTrigger><TabsTrigger value="orders">Orders and captures</TabsTrigger></TabsList>
   <TabsContent value="drafts">
    {data.drafts.length===0?<Card><CardContent className="pt-6 text-sm text-muted-foreground">No drafts in this session yet.</CardContent></Card>:
    <Card><CardContent className="pt-4">
     <ScopeGrid rowData={data.drafts} onRowClicked={e=>setSelected(e.data)} columnDefs={[
      {headerName:'Scope',field:'scopeId',flex:0.7,minWidth:100,cellRenderer:p=><span className="font-mono text-xs">{p.value.slice(0,8)}...</span>},
      {headerName:'Summary',field:'summary',flex:1.8,minWidth:220},
      {headerName:'State',field:'state',flex:0.7,minWidth:110,cellRenderer:p=><StateBadge state={p.value}/>,filter:true},
      {headerName:'Reviewed',field:'reviewedAt',flex:1,minWidth:150,valueFormatter:p=>p.value?new Date(p.value).toLocaleString():'-'}
     ]}/>
    </CardContent></Card>}
   </TabsContent>
   <TabsContent value="orders">
    {data.orders.length===0?<Card><CardContent className="pt-6 text-sm text-muted-foreground">No orders in this session yet.</CardContent></Card>:
    <Card><CardContent className="pt-4">
     <ScopeGrid rowData={data.orders} onRowClicked={e=>setSelectedOrder(e.data)} columnDefs={[
      {headerName:'Order',field:'orderId',flex:0.9,minWidth:120,cellRenderer:p=><span className="font-mono text-xs">{p.value||'(creating)'}</span>},
      {headerName:'State',field:'state',flex:0.7,minWidth:110,cellRenderer:p=><StateBadge state={p.value}/>,filter:true},
      {headerName:'Capture',field:'captureId',flex:0.9,minWidth:110,cellRenderer:p=><span className="font-mono text-xs">{p.value||'-'}</span>},
      {headerName:'Completed',field:'completedAt',flex:1,minWidth:150,valueFormatter:p=>p.value?new Date(p.value).toLocaleString():'-'}
     ]}/>
    </CardContent></Card>}
   </TabsContent>
  </Tabs></Reveal>
  <Dialog open={!!selected} onOpenChange={open=>!open&&setSelected(null)}>
   {selected&&<DialogContent>
    <DialogHeader>
     <DialogTitle>Scope {selected.scopeId.slice(0,8)}...</DialogTitle>
     <DialogDescription>Saved in this browser session only.</DialogDescription>
    </DialogHeader>
    <div className="space-y-3 text-sm">
     <div className="flex items-center justify-between gap-3"><span className="text-muted-foreground">State</span><StateBadge state={selected.state}/></div>
     <div><span className="text-muted-foreground">Full scope id</span><p className="mt-1 break-all font-mono text-xs">{selected.scopeId}</p></div>
     <div><span className="text-muted-foreground">Summary</span><p className="mt-1 leading-relaxed text-secondary-foreground">{selected.summary}</p></div>
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
        {selectedOrder.timeline.map((e,i)=><li key={i}>
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
       {selectedOrder.state==='COMPLETED'&&<Button variant="outline" size="sm" onClick={refund} disabled={refunding}>{refunding?'Refunding...':'Refund this payment'}</Button>}
       {config?.demoTools&&selectedOrder.captureId&&<Button variant="outline" size="sm" onClick={simulate} disabled={simulating}>{simulating?'Simulating...':'Simulate webhook'}</Button>}
      </div>
      <p className="mt-1.5 text-xs text-muted-foreground">Replay sends the same idempotency key again to prove a retry cannot double-charge.{config?.demoTools&&selectedOrder.captureId?' Simulate webhook sends a synthetic PayPal webhook through the real verification path.':''}</p>
      {replayResult&&<p className={`mt-2 rounded-md border px-3 py-2 text-xs ${replayResult.ok?'border-primary/40 bg-accent text-accent-foreground':'border-destructive/40 bg-destructive/10 text-foreground'}`}>{replayResult.text}</p>}
      {refundResult&&<p className={`mt-2 rounded-md border px-3 py-2 text-xs ${refundResult.ok?'border-primary/40 bg-accent text-accent-foreground':'border-destructive/40 bg-destructive/10 text-foreground'}`}>{refundResult.text}</p>}
      {webhookResult&&<p className={`mt-2 rounded-md border px-3 py-2 text-xs ${webhookResult.ok?'border-primary/40 bg-accent text-accent-foreground':'border-destructive/40 bg-destructive/10 text-foreground'}`}>{webhookResult.text}</p>}
     </div>}
    </div>
   </DialogContent>}
  </Dialog>
 </>;
}
