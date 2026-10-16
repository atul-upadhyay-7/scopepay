import test from 'node:test';
import assert from 'node:assert/strict';
import request from 'supertest';
import {createApp} from '../server/app.js';
const origin='http://localhost:5173',session='a-session-0123456789',id='idempotency-0123456789';
const capture={status:'COMPLETED',purchase_units:[{payments:{captures:[{id:'CAPTURE123',status:'COMPLETED',amount:{value:'10.00',currency_code:'USD'}}]}}]};
function fixture(overrides={}){
 const rows=new Map(); let creates=0,captures=0;
 const store={reserve:async r=>{if(rows.has(r.requestId))return false;rows.set(r.requestId,{...r});return true;},get:async k=>rows.get(k),find:async id=>[...rows.values()].find(r=>r.orderId===id),save:async(k,v)=>Object.assign(rows.get(k),v)};
 const paypal={create:async()=>{creates++;return {id:'ORDER12345',status:'CREATED'};},capture:async()=>{captures++;return capture;},...overrides};
 return {app:createApp({paypal,store,clientId:'test-public-id',appOrigin:origin}),rows,counts:()=>({creates,captures})};
}
const post=(app,path,body={},s=session,k=id)=>request(app).post(path).set('Origin',origin).set('x-demo-session',s).set('idempotency-key',k).send(body);
test('health/config expose sandbox, never secret',async()=>{const {app}=fixture();assert.equal((await request(app).get('/api/health')).body.environment,'sandbox');assert.equal((await request(app).get('/api/config')).body.price,'10.00');});
test('server-owned price: injected amount rejected',async()=>{const f=fixture();assert.equal((await post(f.app,'/api/orders',{sku:'sandbox-demo',amount:'0.01'})).status,400);assert.equal(f.counts().creates,0);});
test('unknown sku rejected',async()=>{const f=fixture();assert.equal((await post(f.app,'/api/orders',{sku:'other'})).status,400);});
test('origin required',async()=>{const f=fixture();assert.equal((await request(f.app).post('/api/orders').send({sku:'sandbox-demo'})).status,403);});
test('session required',async()=>{const f=fixture();assert.equal((await post(f.app,'/api/orders',{sku:'sandbox-demo'},'')).status,400);});
test('idempotency key required',async()=>{const f=fixture();assert.equal((await post(f.app,'/api/orders',{sku:'sandbox-demo'},session,'')).status,400);});
test('create retry reuses original order',async()=>{const f=fixture();assert.equal((await post(f.app,'/api/orders',{sku:'sandbox-demo'})).status,201);assert.equal((await post(f.app,'/api/orders',{sku:'sandbox-demo'})).body.orderId,'ORDER12345');assert.equal(f.counts().creates,1);});
test('other browser cannot capture',async()=>{const f=fixture();await post(f.app,'/api/orders',{sku:'sandbox-demo'});assert.equal((await post(f.app,'/api/orders/ORDER12345/capture',{},'different-session-123')).status,404);});
test('capture completes and retry uses stored receipt',async()=>{const f=fixture();await post(f.app,'/api/orders',{sku:'sandbox-demo'});assert.equal((await post(f.app,'/api/orders/ORDER12345/capture')).body.status,'COMPLETED');assert.equal((await post(f.app,'/api/orders/ORDER12345/capture')).body.captureId,'CAPTURE123');assert.equal(f.counts().captures,1);});
test('pending capture never shown as paid',async()=>{const f=fixture({capture:async()=>({status:'APPROVED'})});await post(f.app,'/api/orders',{sku:'sandbox-demo'});assert.equal((await post(f.app,'/api/orders/ORDER12345/capture')).body.status,'PENDING_REVIEW');});
test('wrong captured amount is not accepted',async()=>{const wrong=structuredClone(capture);wrong.purchase_units[0].payments.captures[0].amount.value='0.01';const f=fixture({capture:async()=>wrong});await post(f.app,'/api/orders',{sku:'sandbox-demo'});assert.equal((await post(f.app,'/api/orders/ORDER12345/capture')).status,502);assert.notEqual(f.rows.get(id).state,'COMPLETED');});
test('failed create stays reserved, preventing silent duplicate',async()=>{let calls=0;const f=fixture({create:async()=>{calls++;throw new Error('secret-token-not-for-user');}});await post(f.app,'/api/orders',{sku:'sandbox-demo'});const r=await post(f.app,'/api/orders',{sku:'sandbox-demo'});assert.equal(r.status,409);assert.equal(calls,1);assert.ok(!JSON.stringify(r.body).includes('secret-token'));});
test('unknown capture rejected',async()=>{const f=fixture();assert.equal((await post(f.app,'/api/orders/ORDER55555/capture')).status,404);});
