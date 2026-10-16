import test from 'node:test';import assert from 'node:assert/strict';import {createPayPal} from '../server/paypal.js';
test('PayPal adapter is sandbox-only, sends token and stable request id, reuses OAuth',async()=>{
 const calls=[];const p=createPayPal({clientId:'fake',clientSecret:'test-only',fetchImpl:async(url,options)=>{calls.push({url,options});return {ok:true,json:async()=>url.endsWith('/token')?{access_token:'fake-token',expires_in:3600}:{id:'ORDER12345'}};}});
 await p.create({price:{sku:'test',value:'10.00',currency:'USD'},requestId:'create-123'});await p.capture({orderId:'ORDER12345',requestId:'capture-ORDER12345'});
 assert.equal(calls.length,3);assert.ok(calls.every(c=>c.url.startsWith('https://api-m.sandbox.paypal.com/')));assert.equal(calls[2].options.headers['PayPal-Request-Id'],'capture-ORDER12345');assert.equal(JSON.parse(calls[1].options.body).purchase_units[0].amount.value,'10.00');
});
test('upstream auth secrets not exposed in error',async()=>{const p=createPayPal({clientId:'fake',clientSecret:'test-only',fetchImpl:async()=>({ok:false,status:401})});await assert.rejects(()=>p.create({price:{},requestId:'test'}),/PAYPAL_AUTH_FAILED/);});
