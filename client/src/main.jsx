import React,{useEffect,useRef,useState} from 'react';
import {createRoot} from 'react-dom/client';
import './style.css';
function App(){
  const [config,setConfig]=useState(null),[message,setMessage]=useState('Checking the local sandbox API...'),[ready,setReady]=useState(false);
  const [session]=useState(()=>{let value=sessionStorage.getItem('scopepay-session');if(!value){value=crypto.randomUUID();sessionStorage.setItem('scopepay-session',value);}return value;});
  const key=useRef(null),payment=useRef(null),working=useRef(false);
  async function api(path,body,extra={}){
    const r=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json','x-demo-session':session,...extra},body:JSON.stringify(body)});
    const data=await r.json();if(!r.ok)throw new Error(data.error||'PAYMENT_UNCONFIRMED');return data;
  }
  useEffect(()=>{
    let cancelled=false;let script;
    (async()=>{
      const r=await fetch('/api/config');if(!r.ok)throw new Error('API_UNAVAILABLE');const c=await r.json();
      if(cancelled)return;setConfig(c);
      if(!c.clientId){setMessage('Setup needed: configure sandbox credentials on the server.');return;}
      script=document.createElement('script');script.src='https://www.sandbox.paypal.com/web-sdk/v6/core';
      script.onload=async()=>{try{
        const sdk=await window.paypal.createInstance({clientId:c.clientId,components:['paypal-payments'],pageType:'checkout'});
        const eligibility=await sdk.findEligibleMethods();
        if(!eligibility.isEligible('paypal'))throw new Error('PAYPAL_NOT_ELIGIBLE');
        payment.current=await sdk.createPayPalOneTimePaymentSession({
          onApprove:async({orderId})=>{try{setMessage('Buyer approved. Confirming capture...');const data=await api(`/api/orders/${orderId}/capture`,{});setMessage(`Sandbox payment ${data.status.toLowerCase()}. Capture: ${data.captureId}`);}catch(e){setMessage(`${e.message}. Payment not confirmed. Check sandbox activity before retrying.`);}finally{working.current=false;setReady(false);}},
          onCancel:()=>{working.current=false;setMessage('Checkout cancelled. No completed payment was confirmed. Reload to start a new checkout.');setReady(false);},
          onError:()=>{working.current=false;setMessage('Payment not confirmed. Check sandbox activity before retrying.');setReady(false);}
        });
        if(!cancelled){setReady(true);setMessage('Ready. Review the test item, then approve checkout in PayPal.');}
      }catch(e){if(!cancelled)setMessage(`Sandbox unavailable: ${e.message}`);}};
      script.onerror=()=>setMessage('Could not load the PayPal sandbox SDK. Check your connection.');document.head.appendChild(script);
    })().catch(()=>{if(!cancelled)setMessage('API is not ready. Start MongoDB and the configured sandbox server.');});
    return()=>{cancelled=true;script?.remove();};
  },[]);
  function checkout(){
    if(working.current||!payment.current)return;working.current=true;setReady(false);
    key.current=key.current||crypto.randomUUID();
    const order=api('/api/orders',{sku:'sandbox-demo'},{'idempotency-key':key.current});
    // Keep start in the direct click handler to preserve browser user activation.
    payment.current.start({presentationMode:'auto'},order).catch(()=>{working.current=false;setMessage('Checkout did not finish. Check sandbox activity before trying again.');});
  }
  return <main><header><span className="brand">ScopePay<span className="dot">.</span></span><span className="tag">SANDBOX ONLY</span></header><section className="intro"><p className="eyebrow">PAYMENT CORE / BUILD 01</p><h1>A clear scope.<br/>A confirmed payment.</h1><p className="lead">First, prove the payment loop. Product and AI features come after the scope is agreed.</p></section><section className="card"><div><p className="eyebrow">TEST CHECKOUT</p><h2>Sandbox demo item</h2><p>One fixed-price test item. No real service is sold and no real money moves.</p></div><div className="price">${config?.price||'10.00'} <span>USD</span></div><dl><div><dt>Environment</dt><dd>PayPal sandbox</dd></div><div><dt>Price authority</dt><dd>Server-side catalog</dd></div><div><dt>Payment flow</dt><dd>Create → approve → capture</dd></div></dl><button disabled={!ready} onClick={checkout}>Review &amp; pay with PayPal sandbox</button><p className="status" role="status" aria-live="polite">{message}</p></section><footer>Local development prototype. Not production-ready. No AI functionality yet.</footer></main>;
}
createRoot(document.getElementById('root')).render(<App/>);
