import { chromium } from 'playwright';
const base = 'http://localhost:4517';
const H = {'Content-Type':'application/json'};
const ORDER = {requestId:'idempotency-0123456789', orderId:'ORDER12345', state:'COMPLETED', scopeId:'9908358d-3f92-4fd1-a476-2df6fd720982', captureId:'CAPTURE123', createdAt:'2026-10-23T09:00:00Z', completedAt:'2026-10-23T09:01:00Z', timeline:[{type:'ORDER_CREATED', at:'2026-10-23T09:00:00Z', payload:{orderId:'ORDER12345', amount:'10.00', currency:'USD'}},{type:'ORDER_COMPLETED', at:'2026-10-23T09:01:00Z', payload:{captureId:'CAPTURE123', amount:'10.00', currency:'USD'}}], ledger:[
 {account:'paypal_sandbox_clearing', direction:'DEBIT', amount:'10.00', currency:'USD', memo:'Sandbox capture CAPTURE123'},
 {account:'demo_revenue', direction:'CREDIT', amount:'10.00', currency:'USD', memo:'Fixed sandbox demo item'}]};
const COMPARISON = {comparisons:[
 {deliverable:'Contact form', verdict:'CLAIMED_ADDRESSED', quote:'delivered the contact form', note:'The account explicitly claims this deliverable is done.'},
 {deliverable:'Gallery page', verdict:'NOT_EVIDENCED', quote:'', note:'The account says nothing about this deliverable; that means not evidenced, not proven missing.'}],
 overallNote:'FIXTURE comparison for UI screenshot, not a live AI result. One deliverable claimed, one not evidenced.'};
const RR_DRAFT = {refundRequestId:'rr-fixture-1', orderRequestId:ORDER.requestId, state:'DRAFT', amount:'3.50', currency:'USD', reason:'client reports missing work', comparison:COMPARISON, paypalRefundId:null, createdAt:'2026-10-23T09:10:00Z', completedAt:null};
const RR_DONE = {...RR_DRAFT, state:'COMPLETED', paypalRefundId:'REFUND-PARTIAL-1', completedAt:'2026-10-23T09:12:00Z'};
let phase = 'initial';
function receiptsBody() {
  const rr = phase === 'initial' ? [] : phase === 'draft' ? [RR_DRAFT] : [RR_DONE];
  const ledger = phase === 'done' ? [...ORDER.ledger,
    {account:'demo_revenue', direction:'DEBIT', amount:'3.50', currency:'USD', memo:'Sandbox refund REFUND-PARTIAL-1'},
    {account:'paypal_sandbox_clearing', direction:'CREDIT', amount:'3.50', currency:'USD', memo:'Refund reverses sandbox capture'}] : ORDER.ledger;
  return JSON.stringify({drafts:[], orders:[{...ORDER, ledger}], refundRequests:rr});
}
async function setup(page) {
  await page.route('**/api/**', r => r.fulfill({status:200, headers:H, body:'{}'}));
  await page.route('**/api/config', r => r.fulfill({status:200, headers:H, body:'{}'}));
  await page.route('**/api/receipts', r => r.fulfill({status:200, headers:H, body:receiptsBody()}));
  await page.route('**/api/orders/ORDER12345/refund-requests', r => { phase = 'draft'; r.fulfill({status:201, headers:H, body:JSON.stringify(RR_DRAFT)}); });
  await page.route('**/api/refund-requests/rr-fixture-1/approve', r => { phase = 'done'; r.fulfill({status:200, headers:H, body:JSON.stringify(RR_DONE)}); });
}
async function drive(page, prefix) {
  await page.goto(base + '/receipts', {waitUntil:'networkidle'});
  await page.getByRole('tab', {name:'Orders and captures'}).click();
  await page.locator('.ag-row').first().click();
  await page.getByText('Make-good refund, with evidence comparison').waitFor();
  await page.getByPlaceholder('What was delivered, in your own words (self-reported account)').fill('I delivered the contact form on Tuesday. The gallery page is not part of what I sent. FIXTURE text for UI screenshot.');
  await page.getByPlaceholder('0.00').fill('3.50');
  await page.getByPlaceholder('Reason (e.g. client reports missing work)').fill('client reports missing work');
  await page.getByRole('button', {name:'Compare and prepare refund'}).click();
  await page.getByText('Comparison - claim coverage only, not verified delivery').waitFor();
  console.log(prefix, 'overflow:', await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
  await page.screenshot({path:`/downloads/mg-${prefix}-comparison.png`});
}
const browser = await chromium.launch();
{ // desktop: comparison, then approve -> completed
  const ctx = await browser.newContext({viewport:{width:1280,height:1000}, deviceScaleFactor:2});
  const page = await ctx.newPage();
  await setup(page);
  await drive(page, 'desktop');
  await page.getByRole('button', {name:'Approve this refund'}).click();
  await page.getByText('Make-good refund completed in the sandbox', {exact:false}).waitFor();
  const legacyCount = await page.getByRole('button', {name:'Refund this payment'}).count();
  console.log('legacy full-refund button visible with make-good present:', legacyCount, legacyCount === 0 ? 'HIDDEN-OK' : 'GUARD-FAIL');
  await page.screenshot({path:'/downloads/mg-desktop-completed.png'});
  await ctx.close();
}
{ // mobile: comparison view, then approve -> completed (legacy button must be hidden)
  const ctx = await browser.newContext({viewport:{width:390,height:844}, deviceScaleFactor:2, isMobile:true, hasTouch:true});
  const page = await ctx.newPage();
  await setup(page);
  await drive(page, 'mobile');
  await page.locator('[role="dialog"]').evaluate(el => { el.scrollTop = el.scrollHeight; });
  await page.waitForTimeout(300);
  await page.screenshot({path:'/downloads/mg-mobile-bottom.png'});
  await page.getByRole('button', {name:'Approve this refund'}).click();
  await page.getByText('Make-good refund completed in the sandbox', {exact:false}).waitFor();
  const legacyMobile = await page.getByRole('button', {name:'Refund this payment'}).count();
  console.log('mobile legacy full-refund button with make-good present:', legacyMobile, legacyMobile === 0 ? 'HIDDEN-OK' : 'GUARD-FAIL');
  await page.locator('[role="dialog"]').evaluate(el => { el.scrollTop = 0; });
  await page.waitForTimeout(300);
  await page.screenshot({path:'/downloads/mg-mobile-completed.png'});
  await ctx.close();
}
await browser.close();
console.log('SHOTS3 DONE');
