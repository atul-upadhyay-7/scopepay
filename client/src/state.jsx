import React,{createContext,useContext,useEffect,useRef,useState} from 'react';
export const EXAMPLE='Build a three-page portfolio website: Home, Projects and Contact. Include a contact form. Do not include visitor login. Deliver responsive layouts. Deadline is not agreed yet.';
const ScopePayContext=createContext(null);
export function ScopePayProvider({children}){
 const [config,setConfig]=useState(null),[brief,setBrief]=useState(EXAMPLE),[draft,setDraft]=useState(null),[review,setReview]=useState(null),[checked,setChecked]=useState(false),[busy,setBusy]=useState(false),[aiMessage,setAiMessage]=useState(''),[message,setMessage]=useState('Checking sandbox setup...'),[ready,setReady]=useState(false),[paid,setPaid]=useState(null);
 const [setupIssue,setSetupIssue]=useState(false),[checkoutClosed,setCheckoutClosed]=useState(false);
 const [session]=useState(()=>{let value=sessionStorage.getItem('scopepay-session');if(!value){value=crypto.randomUUID();sessionStorage.setItem('scopepay-session',value);}return value;});
 const payment=useRef(null),working=useRef(false),key=useRef(null);
 async function api(path,body,extra={}){const r=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json','x-demo-session':session,...extra},body:JSON.stringify(body)});const data=await r.json();if(!r.ok)throw new Error(data.error||'REQUEST_UNCONFIRMED');return data;}
 useEffect(()=>{let cancelled=false,script;(async()=>{
  const r=await fetch('/api/config');if(!r.ok)throw new Error('API_UNAVAILABLE');const c=await r.json();if(cancelled)return;setConfig(c);
  if(!c.clientId){setMessage('Setup needed: configure sandbox credentials on the server.');setSetupIssue(true);return;}
  script=document.createElement('script');script.src='https://www.sandbox.paypal.com/web-sdk/v6/core';
  script.onload=async()=>{try{
   const sdk=await window.paypal.createInstance({clientId:c.clientId,components:['paypal-payments'],pageType:'checkout'});const eligibility=await sdk.findEligibleMethods();if(!eligibility.isEligible('paypal'))throw new Error('PAYPAL_NOT_ELIGIBLE');
   payment.current=await sdk.createPayPalOneTimePaymentSession({
    onApprove:async({orderId})=>{try{setMessage('Buyer approved. Confirming capture...');const data=await api(`/api/orders/${orderId}/capture`,{});setMessage(`Sandbox payment ${data.status.toLowerCase()}. Capture: ${data.captureId}`);setPaid({orderId,captureId:data.captureId});}catch(e){setMessage(`${e.message}. Payment not confirmed. Check sandbox activity before retrying.`);}finally{working.current=false;setReady(false);}},
    onCancel:()=>{working.current=false;setMessage('Checkout cancelled. No completed payment confirmed. Reload before starting another test.');setReady(false);setCheckoutClosed(true);},
    onError:()=>{working.current=false;setMessage('Payment not confirmed. Check sandbox activity before retrying.');setReady(false);setCheckoutClosed(true);}
   });if(!cancelled){setReady(true);setMessage('PayPal sandbox ready. Review the draft before checkout.');}
  }catch(e){if(!cancelled){setMessage(`Sandbox unavailable: ${e.message}`);setSetupIssue(true);}}};
  script.onerror=()=>{setMessage('Could not load the PayPal sandbox SDK.');setSetupIssue(true);};document.head.appendChild(script);
 })().catch(()=>{if(!cancelled){setMessage('API is not ready. Start the configured server.');setSetupIssue(true);}});return()=>{cancelled=true;script?.remove();};},[]);
 async function extract(){setBusy(true);setAiMessage('Extracting a draft. Nothing is approved or charged.');setDraft(null);setReview(null);setChecked(false);setPaid(null);try{const data=await api('/api/scopes/extract',{brief});setDraft(data);setAiMessage('Draft only. Check the source evidence and open questions.');return data;}catch(e){setAiMessage(`${e.message}. No draft was accepted. AI is not replaced by a canned result.`);return null;}finally{setBusy(false);}}
 async function confirmReview(){if(!draft)return null;setBusy(true);try{const data=await api(`/api/scopes/${draft.scopeId}/review`,{acknowledge:true});setReview(data);setAiMessage('Reviewed for a sandbox demo only. Open questions remain listed. This is not a client contract.');return data;}catch(e){setAiMessage(`${e.message}. Review was not saved.`);return null;}finally{setBusy(false);}}
 function checkout(){if(working.current||!payment.current||!review)return;working.current=true;setReady(false);key.current=key.current||crypto.randomUUID();const order=api('/api/orders',{sku:'sandbox-demo',scopeId:review.scopeId},{'idempotency-key':key.current});payment.current.start({presentationMode:'auto'},order).catch(()=>{working.current=false;setMessage('Checkout did not finish. Check sandbox activity before trying again.');});}
 async function replayOrder(o){const r=await fetch('/api/orders',{method:'POST',headers:{'Content-Type':'application/json','x-demo-session':session,'idempotency-key':o.requestId},body:JSON.stringify({sku:'sandbox-demo',scopeId:o.scopeId})});const data=await r.json();return{status:r.status,data};}
 async function refundOrder(o){const r=await fetch(`/api/orders/${o.orderId}/refund`,{method:'POST',headers:{'Content-Type':'application/json','x-demo-session':session},body:JSON.stringify({})});const data=await r.json();return{status:r.status,data};}
 async function simulateWebhook(requestId){const r=await fetch('/api/demo/webhook-simulator',{method:'POST',headers:{'Content-Type':'application/json','x-demo-session':session},body:JSON.stringify({requestId})});const data=await r.json();return{status:r.status,data};}
 async function setFailureMode(mode){const r=await fetch('/api/demo/failure-mode',{method:'POST',headers:{'Content-Type':'application/json','x-demo-session':session},body:JSON.stringify({mode})});const data=await r.json();if(!r.ok)throw new Error(data.error||'FAILURE_MODE_UNAVAILABLE');return data.mode;}
 async function loadReceipts(){const r=await fetch('/api/receipts',{headers:{'x-demo-session':session}});const data=await r.json();if(!r.ok)throw new Error(data.error||'RECEIPTS_UNAVAILABLE');return data;}
 const stepDone={brief:true,draft:!!draft,review:!!review,paid:!!paid};
 return <ScopePayContext.Provider value={{config,brief,setBrief:v=>{setBrief(v);setDraft(null);setChecked(false);},draft,review,checked,setChecked,busy,aiMessage,message,ready,setupIssue,checkoutClosed,paid,extract,confirmReview,checkout,loadReceipts,replayOrder,refundOrder,simulateWebhook,setFailureMode,stepDone}}>{children}</ScopePayContext.Provider>;
}
export function useScopePay(){return useContext(ScopePayContext);}
