import express from 'express';
import helmet from 'helmet';
import {createHash,randomUUID} from 'node:crypto';
import {createScopeExtractor,ScopeError} from './scope/extract.js';
export const PRICE=Object.freeze({sku:'sandbox-demo',value:'10.00',currency:'USD'});
const keyPattern=/^[A-Za-z0-9-]{16,80}$/;
const orderPattern=/^[A-Z0-9]{5,40}$/;
export function createApp({paypal,store,clientId,appOrigin='http://localhost:5173',scopeProvider,requireReviewedScope=false}) {
  const app=express();
  app.disable('x-powered-by'); app.use(helmet()); app.use(express.json({limit:'8kb'}));
  const owner=req=>createHash('sha256').update(req.get('x-demo-session')||'').digest('hex');
  app.get('/api/health',(_req,res)=>res.json({status:'ok',environment:'sandbox',database:'connected'}));
  app.get('/api/config',(_req,res)=>res.json({clientId,currency:PRICE.currency,price:PRICE.value,sku:PRICE.sku,environment:'sandbox',aiConfigured:!!scopeProvider,syntheticOnly:true}));
  app.use(['/api/orders','/api/scopes'],(req,res,next)=>{
    if(req.get('origin')!==appOrigin) return res.status(403).json({error:'ORIGIN_NOT_ALLOWED'});
    if(!keyPattern.test(req.get('x-demo-session')||'')) return res.status(400).json({error:'SESSION_REQUIRED'});
    next();
  });
  const extractScope=createScopeExtractor({provider:scopeProvider});
  app.post('/api/scopes/extract',async(req,res,next)=>{
    if(!req.body||Object.keys(req.body).length!==1||!Object.hasOwn(req.body,'brief'))return res.status(400).json({error:'INVALID_BRIEF_REQUEST'});
    try {const draft=await extractScope(req.body.brief);
      if(!store.saveDraft)return res.json(draft);
      const scopeId=randomUUID();await store.saveDraft({scopeId,owner:owner(req),...draft,createdAt:new Date()});
      res.json({scopeId,...draft});}
    catch(e){if(e instanceof ScopeError)return res.status(e.status).json({error:e.code});next(e);}
  });
  app.post('/api/scopes/:scopeId/review',async(req,res,next)=>{
    try{
      if(!req.body||req.body.acknowledge!==true||Object.keys(req.body).length!==1)return res.status(400).json({error:'REVIEW_ACKNOWLEDGMENT_REQUIRED'});
      const draft=await store.findDraft?.(req.params.scopeId);
      if(!draft||draft.owner!==owner(req))return res.status(404).json({error:'SCOPE_NOT_FOUND'});
      await store.reviewDraft(draft.scopeId,owner(req));res.json({scopeId:draft.scopeId,state:'REVIEWED_FOR_SANDBOX'});
    }catch(e){next(e);}
  });
  app.post('/api/orders',async(req,res,next)=>{
    try {
      if(req.body?.sku!==PRICE.sku || Object.keys(req.body).some(k=>!['sku','scopeId'].includes(k)))return res.status(400).json({error:'INVALID_PRODUCT'});
      if(requireReviewedScope||req.body.scopeId){
        const draft=typeof req.body.scopeId==='string'&&await store.findDraft?.(req.body.scopeId);
        if(!draft||draft.owner!==owner(req)||draft.state!=='REVIEWED_FOR_SANDBOX')return res.status(409).json({error:'REVIEWED_SCOPE_REQUIRED'});
      }
      const requestId=req.get('idempotency-key');
      if(!keyPattern.test(requestId||''))return res.status(400).json({error:'IDEMPOTENCY_KEY_REQUIRED'});
      const reservation={requestId,owner:owner(req),state:'CREATING',scopeId:req.body.scopeId||null,createdAt:new Date()};
      if(!await store.reserve(reservation)) {
        const existing=await store.get(requestId);
        if(existing.owner!==owner(req))return res.status(409).json({error:'REQUEST_CONFLICT'});
        return existing.orderId ? res.json({orderId:existing.orderId}) : res.status(409).json({error:'CREATE_PENDING_REVIEW'});
      }
      const data=await paypal.create({price:PRICE,requestId});
      if(!orderPattern.test(data.id||''))throw new Error('Invalid PayPal order response');
      await store.save(requestId,{orderId:data.id,state:data.status||'CREATED'});
      res.status(201).json({orderId:data.id});
    } catch(e){next(e);}
  });
  app.post('/api/orders/:orderId/capture',async(req,res,next)=>{
    try {
      if(!orderPattern.test(req.params.orderId))return res.status(400).json({error:'INVALID_ORDER'});
      const order=await store.find(req.params.orderId);
      if(!order || order.owner!==owner(req))return res.status(404).json({error:'ORDER_NOT_FOUND'});
      if(order.state==='COMPLETED')return res.json({status:'COMPLETED',captureId:order.captureId});
      const data=await paypal.capture({orderId:order.orderId,requestId:`capture-${order.orderId}`});
      const capture=data.purchase_units?.[0]?.payments?.captures?.[0];
      if(data.status!=='COMPLETED'||capture?.status!=='COMPLETED') {
        await store.save(order.requestId,{state:'PENDING_REVIEW'});
        return res.status(409).json({error:'CAPTURE_NOT_COMPLETED',status:'PENDING_REVIEW'});
      }
      if(capture.amount?.value!==PRICE.value||capture.amount?.currency_code!==PRICE.currency)throw new Error('Capture amount mismatch');
      await store.save(order.requestId,{state:'COMPLETED',captureId:capture.id,completedAt:new Date()});
      res.json({status:'COMPLETED',captureId:capture.id});
    } catch(e){next(e);}
  });
  app.use((err,_req,res,_next)=>{
    // No upstream bodies, tokens or credential-bearing messages in responses/logs.
    const known=new Set(['INSTRUMENT_DECLINED','PAYER_ACTION_REQUIRED','PAYPAL_AUTH_FAILED']);
    res.status(err.type==='entity.parse.failed'?400:502).json({error:known.has(err.issue)?err.issue:'PAYMENT_UNCONFIRMED',message:'Payment was not confirmed. Check the sandbox activity before trying again.'});
  });
  return app;
}
